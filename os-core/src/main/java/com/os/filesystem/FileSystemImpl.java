package com.os.filesystem;

import com.os.core.interfaces.FileSystemAPI;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Minimal FileSystem implementation backed by Disk + FAT.
 * - Supports root directory in block 2 (8 entries)
 * - Supports creating/reading/deleting files and directories (single-level and simple subdirectories)
 * - loadExecutable returns map { content: String, isExecutable: boolean }
 */
public class FileSystemImpl implements FileSystemAPI {
    private final Disk disk;
    private FAT fat;
    private final Timer saveTimer = new Timer("disk-save-timer", true);
    private TimerTask pendingSaveTask = null;
    private final Object saveLock = new Object();

    public FileSystemImpl(Disk disk) {
        this.disk = disk;
        this.fat = new FAT(disk);
        // ensure FAT loaded/initialized
        this.fat.loadFAT();
    }

    // Schedule a debounced save (1s) to persist disk image
    private void scheduleSave() {
        synchronized (saveLock) {
            if (pendingSaveTask != null) pendingSaveTask.cancel();
            pendingSaveTask = new TimerTask() {
                @Override
                public void run() {
                    try { disk.saveToFile(Disk.DEFAULT_DISK_IMAGE); } catch (Exception ignored) {}
                }
            };
            saveTimer.schedule(pendingSaveTask, 1000);
        }
    }

    // Helper: read raw bytes of a file by first block
    private byte[] readFileBlocks(int firstBlock) {
        if (firstBlock < 0) return new byte[0];
        List<Integer> blocks = fat.getFileBlocks(firstBlock);
        if (blocks == null || blocks.isEmpty()) return new byte[0];
        byte[] buf = new byte[blocks.size() * Disk.BLOCK_SIZE];
        int pos = 0;
        byte[] tmp = new byte[Disk.BLOCK_SIZE];
        for (int b : blocks) {
            int r = disk.readBlock(b, tmp, 0, Disk.BLOCK_SIZE);
            if (r > 0) {
                System.arraycopy(tmp, 0, buf, pos, r);
                pos += r;
            }
        }
        return Arrays.copyOf(buf, pos);
    }

    // Helper: write bytes into blocks, allocate as needed
    private int writeToNewFile(byte[] content) {
        int blocksNeeded = Math.max(1, (content.length + Disk.BLOCK_SIZE - 1) / Disk.BLOCK_SIZE);
        int first = fat.allocateFile(blocksNeeded);
        if (first < 0) return -1;
        List<Integer> blocks = fat.getFileBlocks(first);
        if (blocks == null) return -1;
        int pos = 0;
        for (int b : blocks) {
            int len = Math.min(Disk.BLOCK_SIZE, content.length - pos);
            if (len <= 0) len = 0;
            byte[] blockData = new byte[Disk.BLOCK_SIZE];
            if (len > 0) System.arraycopy(content, pos, blockData, 0, len);
            disk.writeBlock(b, blockData, 0, Disk.BLOCK_SIZE);
            pos += len;
        }
        return first;
    }

    // Root directory helpers
    private List<DirectoryEntry> readDirectoryFromBlock(int firstBlock) {
        List<DirectoryEntry> list = new ArrayList<>();
        if (firstBlock < 0) return list;
        // special-case root directory: stored directly in block ROOT_DIR_BLOCK
        if (firstBlock == FAT.ROOT_DIR_BLOCK) {
            byte[] data = new byte[Disk.BLOCK_SIZE];
            int r = disk.readBlock(firstBlock, data, 0, Disk.BLOCK_SIZE);
            if (r <= 0) return list;
            int entries = Disk.BLOCK_SIZE / DirectoryEntry.ENTRY_SIZE;
            for (int i = 0; i < entries; i++) {
                byte[] entryBytes = Arrays.copyOfRange(data, i * DirectoryEntry.ENTRY_SIZE, (i + 1) * DirectoryEntry.ENTRY_SIZE);
                DirectoryEntry de = DirectoryEntry.fromBytes(entryBytes);
                if (!de.getName().isEmpty()) list.add(de);
            }
            return list;
        }
        List<Integer> blocks = fat.getFileBlocks(firstBlock);
        if (blocks == null || blocks.isEmpty()) return list;
        // read all bytes from chained blocks
        byte[] all = new byte[blocks.size() * Disk.BLOCK_SIZE];
        int pos = 0;
        byte[] tmp = new byte[Disk.BLOCK_SIZE];
        for (int b : blocks) {
            int r = disk.readBlock(b, tmp, 0, Disk.BLOCK_SIZE);
            if (r > 0) {
                System.arraycopy(tmp, 0, all, pos, r);
                pos += r;
            }
        }
        int totalEntries = pos / DirectoryEntry.ENTRY_SIZE;
        for (int i = 0; i < totalEntries; i++) {
            int start = i * DirectoryEntry.ENTRY_SIZE;
            int end = start + DirectoryEntry.ENTRY_SIZE;
            if (end > pos) break;
            byte[] entryBytes = Arrays.copyOfRange(all, start, end);
            DirectoryEntry de = DirectoryEntry.fromBytes(entryBytes);
            if (!de.getName().isEmpty()) list.add(de);
        }
        return list;
    }

    private boolean writeDirectoryToBlock(int firstBlock, List<DirectoryEntry> entries) {
        // special-case root directory: write directly to ROOT_DIR_BLOCK
        if (firstBlock == FAT.ROOT_DIR_BLOCK) {
            int maxEntries = Disk.BLOCK_SIZE / DirectoryEntry.ENTRY_SIZE;
            if (entries.size() > maxEntries) {
                throw new IllegalStateException("目录已满，根目录最多 " + maxEntries + " 个项目");
            }
            byte[] data = new byte[Disk.BLOCK_SIZE];
            int i = 0;
            for (; i < entries.size(); i++) {
                byte[] eb = entries.get(i).toBytes();
                System.arraycopy(eb, 0, data, i * DirectoryEntry.ENTRY_SIZE, DirectoryEntry.ENTRY_SIZE);
            }
            // remaining entries bytes are zero-filled
            return disk.writeBlock(firstBlock, data, 0, Disk.BLOCK_SIZE) == Disk.BLOCK_SIZE;
        }

        // serialize entries into bytes for chained directory blocks
        int totalBytes = entries.size() * DirectoryEntry.ENTRY_SIZE;
        int blocksNeeded = Math.max(1, (totalBytes + Disk.BLOCK_SIZE - 1) / Disk.BLOCK_SIZE);

        // Try to reuse existing blocks in the directory chain if present
        List<Integer> currentBlocks = fat.getFileBlocks(firstBlock);
        if (currentBlocks == null || currentBlocks.isEmpty()) {
            // allocate fresh chain for this directory
            int newFirst = fat.allocateFile(blocksNeeded);
            if (newFirst < 0) return false;
            currentBlocks = fat.getFileBlocks(newFirst);
            if (currentBlocks == null || currentBlocks.size() < blocksNeeded) return false;
        } else {
            // if existing chain too short, append more blocks
            if (currentBlocks.size() < blocksNeeded) {
                int last = currentBlocks.get(currentBlocks.size() - 1);
                int appended = fat.appendToFile(last, blocksNeeded - currentBlocks.size());
                if (appended == -1) return false;
                currentBlocks = fat.getFileBlocks(firstBlock);
                if (currentBlocks == null) return false;
            }
        }

        // write entries into the available blocks (do not change the directory's firstBlock)
        byte[] all = new byte[blocksNeeded * Disk.BLOCK_SIZE];
        int p = 0;
        for (DirectoryEntry e : entries) {
            byte[] eb = e.toBytes();
            System.arraycopy(eb, 0, all, p, DirectoryEntry.ENTRY_SIZE);
            p += DirectoryEntry.ENTRY_SIZE;
        }
        int pos = 0;
        for (int b : currentBlocks) {
            int len = Math.min(Disk.BLOCK_SIZE, all.length - pos);
            byte[] blockData = new byte[Disk.BLOCK_SIZE];
            if (len > 0) System.arraycopy(all, pos, blockData, 0, len);
            disk.writeBlock(b, blockData, 0, Disk.BLOCK_SIZE);
            pos += len;
            if (pos >= all.length) break;
        }
        return true;
    }

    // Write an empty directory block at the given block number without modifying FAT
    private boolean writeEmptyDirectoryBlock(int block) {
        if (block < FAT.DATA_START_BLOCK || block >= Disk.BLOCK_COUNT) return false;
        byte[] data = new byte[Disk.BLOCK_SIZE];
        return disk.writeBlock(block, data, 0, Disk.BLOCK_SIZE) == Disk.BLOCK_SIZE;
    }

    // Resolve a directory path and return its firstBlock. If createIfMissing true, create intermediate directories.
    private int resolveDirectoryFirstBlock(String[] parts, boolean createIfMissing) {
        int currentBlock = FAT.ROOT_DIR_BLOCK;
        for (String part : parts) {
            if (part == null || part.isEmpty()) continue;
            String[] ne = splitNameExt(part);
            String name = ne[0];
            DirectoryEntry de = findInDirectory(currentBlock, name, "");
            if (de != null && de.isDirectory()) {
                currentBlock = de.getFirstBlock();
                continue;
            }
            if (!createIfMissing) return -1;
            // create new directory under currentBlock
            int block = fat.allocateFile(1);
            if (block < 0) return -1;
            // initialize the new directory block without freeing it
            writeEmptyDirectoryBlock(block);
            DirectoryEntry newDir = DirectoryEntry.createDirectory(name, block);
            // add to parent entries
            List<DirectoryEntry> parentEntries = readDirectoryFromBlock(currentBlock);
            parentEntries.add(newDir);
            writeDirectoryToBlock(currentBlock, parentEntries);
            currentBlock = block;
        }
        return currentBlock;
    }

    private DirectoryEntry findInDirectory(int dirBlock, String name, String ext) {
        List<DirectoryEntry> entries = readDirectoryFromBlock(dirBlock);
        for (DirectoryEntry e : entries) {
            if (e.getName().equals(name) && e.getExtension().equals(ext)) return e;
        }
        return null;
    }

    // Helper to add or replace an entry in a directory block chain
    private boolean addOrReplaceDirectoryEntry(int dirBlock, DirectoryEntry entry) {
        List<DirectoryEntry> entries = readDirectoryFromBlock(dirBlock);
        boolean replaced = false;
        for (int i = 0; i < entries.size(); i++) {
            DirectoryEntry d = entries.get(i);
            if (d.getName().equals(entry.getName()) && d.getExtension().equals(entry.getExtension())) {
                entries.set(i, entry);
                replaced = true;
                break;
            }
        }
        if (!replaced) entries.add(entry);
        return writeDirectoryToBlock(dirBlock, entries);
    }

    private boolean removeDirectoryEntry(int dirBlock, String name, String ext) {
        List<DirectoryEntry> entries = readDirectoryFromBlock(dirBlock);
        boolean removed = entries.removeIf(e -> e.getName().equals(name) && e.getExtension().equals(ext));
        if (!removed) return false;
        return writeDirectoryToBlock(dirBlock, entries);
    }

    private String[] splitNameExt(String filename) {
        String name = filename;
        String ext = "";
        int dot = filename.indexOf('.');
        if (dot >= 0) {
            name = filename.substring(0, dot);
            ext = filename.substring(dot + 1);
        }
        // Enforce limits but do not silently truncate: report error to caller
        if (name.length() > 3 || ext.length() > 2) {
            throw new IllegalArgumentException("文件名最多 3 个字符，扩展名最多 2 个字符");
        }
        return new String[] { name, ext };
    }

    // Parse path: only supports /name or /dir/name for now
    private static String[] normalizePathParts(String path) {
        if (path == null) return new String[0];
        String p = path.trim();
        if (p.startsWith("/")) p = p.substring(1);
        if (p.endsWith("/")) p = p.substring(0, p.length() - 1);
        if (p.isEmpty()) return new String[0];
        return p.split("/");
    }

    // FileSystemAPI implementations
    @Override
    public List<Map<String, Object>> listDirectory(String path) {
        String[] parts = normalizePathParts(path);
        int dirBlock = FAT.ROOT_DIR_BLOCK;
        if (parts.length == 0) {
            List<DirectoryEntry> entries = readDirectoryFromBlock(dirBlock);
            List<Map<String,Object>> out = new ArrayList<>();
            for (DirectoryEntry e : entries) {
                Map<String,Object> m = new HashMap<>();
                m.put("name", e.getName());
                m.put("ext", e.getExtension());
                m.put("attrs", e.getAttributes());
                m.put("firstBlock", e.getFirstBlock());
                // compute actual byte size of file content for frontend accuracy
                int sizeBytes = 0;
                if (!e.isDirectory()) {
                    byte[] data = readFileBlocks(e.getFirstBlock());
                    sizeBytes = data == null ? 0 : data.length;
                }
                m.put("size", e.getFileSize());
                m.put("sizeBytes", sizeBytes);
                out.add(m);
            }
            return out;
        }
        // resolve the directory
        int target = resolveDirectoryFirstBlock(parts, false);
        if (target < 0) return Collections.emptyList();
        List<DirectoryEntry> entries = readDirectoryFromBlock(target);
        List<Map<String,Object>> res = new ArrayList<>();
        for (DirectoryEntry se : entries) {
            Map<String,Object> m = new HashMap<>();
            m.put("name", se.getName());
            m.put("ext", se.getExtension());
            m.put("attrs", se.getAttributes());
            m.put("firstBlock", se.getFirstBlock());
            int sizeBytes = 0;
            if (!se.isDirectory()) {
                byte[] data = readFileBlocks(se.getFirstBlock());
                sizeBytes = data == null ? 0 : data.length;
            }
            m.put("size", se.getFileSize());
            m.put("sizeBytes", sizeBytes);
            res.add(m);
        }
        return res;
    }

    @Override
    public boolean createDirectory(String path) {
        String[] parts = normalizePathParts(path);
        if (parts.length == 0) return false;
        // use resolver to create intermediate directories
        int block = resolveDirectoryFirstBlock(parts, true);
        return block >= 0;
    }

    @Override
    public boolean deleteDirectory(String path, boolean recursive) {
        String[] parts = normalizePathParts(path);
        if (parts.length == 0) return false;
        int dirBlock = resolveDirectoryFirstBlock(parts, false);
        if (dirBlock < 0) return false;
        if (dirBlock == FAT.ROOT_DIR_BLOCK) return false; // do not delete root
        List<DirectoryEntry> sub = readDirectoryFromBlock(dirBlock);
        if (!recursive && !sub.isEmpty()) return false;

        if (recursive && !sub.isEmpty()) {
            // Recursively delete children first to release their blocks
            for (DirectoryEntry child : new ArrayList<>(sub)) {
                String childPath;
                if (path.endsWith("/")) childPath = path + child.getName();
                else childPath = path + "/" + child.getName();
                if (!child.isDirectory() && !child.getExtension().isEmpty()) {
                    childPath += "." + child.getExtension();
                }
                boolean ok = child.isDirectory()
                        ? deleteDirectory(childPath, true)
                        : deleteFile(childPath);
                if (!ok) {
                    // abort if any child removal fails
                    return false;
                }
            }
            // refresh directory entries after child removals
            sub = readDirectoryFromBlock(dirBlock);
        }

        if (!sub.isEmpty()) return false; // still not empty -> cannot delete

        fat.freeFile(dirBlock);
        // remove from parent
        String[] parentParts = Arrays.copyOf(parts, parts.length - 1);
        int parentBlock = parentParts.length == 0 ? FAT.ROOT_DIR_BLOCK : resolveDirectoryFirstBlock(parentParts, false);
        if (parentBlock >= 0) {
            String[] ne = splitNameExt(parts[parts.length - 1]);
            removeDirectoryEntry(parentBlock, ne[0], "");
        }
        scheduleSave();
        try { reconcileDiskWithFAT(); } catch (Exception ignored) {}
        try { reconcileFATOrphans(); } catch (Exception ignored) {}
        return true;
    }

    @Override
    public boolean createFile(String path, String content, boolean isExecutable, boolean overwrite) {
        String[] parts = normalizePathParts(path);
        if (parts.length == 0) return false;
        String filename = parts[parts.length - 1];
        String[] ne = splitNameExt(filename);
        String nm = ne[0];
        String ext = ne[1];
        int dirBlock;
        if (parts.length == 1) dirBlock = FAT.ROOT_DIR_BLOCK;
        else {
            String[] parent = Arrays.copyOf(parts, parts.length - 1);
            dirBlock = resolveDirectoryFirstBlock(parent, false);
            if (dirBlock < 0) return false;
        }
        DirectoryEntry existing = findInDirectory(dirBlock, nm, ext);
        if (existing != null && !overwrite) return false;
        byte[] bytes = (content == null ? new byte[0] : content.getBytes(StandardCharsets.UTF_8));

        // If file exists and content is identical, do nothing (avoid reallocating blocks)
        if (existing != null) {
            try {
                byte[] old = readFileBlocks(existing.getFirstBlock());
                if (Arrays.equals(old, bytes)) {
                    // No-op when content unchanged
                    return true;
                }
            } catch (Exception ignored) {}
        }

        // Write new file blocks first. If there's an existing file and overwrite is true,
        // we will free the old blocks only after successfully adding/replacing directory entry.
        int first = writeToNewFile(bytes);
        if (first < 0) return false;
        DirectoryEntry entry = DirectoryEntry.createFile(nm, ext, first, Math.min(bytes.length, 255));
        // set executable attr
        if (isExecutable) entry.setAttributes(entry.getAttributes() | DirectoryEntry.ATTR_EXECUTABLE);

        // add/replace directory entry
        boolean ok = addOrReplaceDirectoryEntry(dirBlock, entry);
        if (!ok) {
            // failed to add entry: free newly allocated blocks to avoid leak
            fat.freeFile(first);
            return false;
        }

        // if overwriting an existing file, free its previous blocks now (avoid freeing the newly allocated chain)
        if (existing != null) {
            int oldFirst = existing.getFirstBlock();
            if (oldFirst != entry.getFirstBlock()) {
                try { fat.freeFile(oldFirst); } catch (Exception ignored) {}
            }
        }

        scheduleSave();
        // after creating, reconcile any orphan disk bits with FAT
        try { reconcileDiskWithFAT(); } catch (Exception ignored) {}
        return true;
    }

    @Override
    public String readFile(String path) {
        String[] parts = normalizePathParts(path);
        if (parts.length == 0) return "";
        String filename = parts[parts.length - 1];
        String[] ne = splitNameExt(filename);
        String nm = ne[0];
        String ext = ne[1];
        int dirBlock;
        if (parts.length == 1) dirBlock = FAT.ROOT_DIR_BLOCK;
        else {
            String[] parent = Arrays.copyOf(parts, parts.length - 1);
            dirBlock = resolveDirectoryFirstBlock(parent, false);
            if (dirBlock < 0) return "";
        }
        DirectoryEntry de = findInDirectory(dirBlock, nm, ext);
        if (de == null) return "";
        byte[] data = readFileBlocks(de.getFirstBlock());
        if (data == null || data.length == 0) return "";
        return new String(data, StandardCharsets.UTF_8).trim();
    }

    @Override
    public boolean writeFile(String path, String content, boolean append) {
        // For simplicity overwrite behavior: delete existing then create new file
        if (!deleteFile(path)) {
            // if didn't exist, proceed
        }
        boolean res = createFile(path, content, false, true);
        if (res) scheduleSave();
        return res;
    }

    @Override
    public boolean deleteFile(String path) {
        String[] parts = normalizePathParts(path);
        if (parts.length == 0) return false;
        String filename = parts[parts.length - 1];
        String[] ne = splitNameExt(filename);
        String nm = ne[0];
        String ext = ne[1];
        int dirBlock;
        if (parts.length == 1) dirBlock = FAT.ROOT_DIR_BLOCK;
        else {
            String[] parent = Arrays.copyOf(parts, parts.length - 1);
            dirBlock = resolveDirectoryFirstBlock(parent, false);
            if (dirBlock < 0) return false;
        }
        DirectoryEntry de = findInDirectory(dirBlock, nm, ext);
        if (de == null) return false;
        fat.freeFile(de.getFirstBlock());
        // remove from directory
        boolean ok = removeDirectoryEntry(dirBlock, nm, ext);
        if (ok) {
            scheduleSave();
            try { reconcileDiskWithFAT(); } catch (Exception ignored) {}
        }
        return ok;
    }

    @Override
    public boolean copyFile(String sourcePath, String destPath, boolean overwrite) {
        String content = readFile(sourcePath);
        if (content == null) return false;
        boolean ok = createFile(destPath, content, false, overwrite);
        if (ok) scheduleSave();
        return ok;
    }

    @Override
    public boolean moveFile(String sourcePath, String destPath, boolean overwrite) {
        boolean ok = copyFile(sourcePath, destPath, overwrite);
        if (!ok) return false;
        return deleteFile(sourcePath);
    }

    @Override
    public Map<String, Object> loadExecutable(String path, boolean validateOnly) {
        Map<String,Object> r = new HashMap<>();
        String content = readFile(path);
        if (content == null || content.isEmpty()) {
            r.put("found", false);
            return r;
        }
        // find entry to check executable flag
        String[] parts = normalizePathParts(path);
        String filename = parts[parts.length - 1];
        String[] ne = splitNameExt(filename);
        String nm = ne[0];
        String ext = ne[1];
        int dirBlock;
        if (parts.length == 1) dirBlock = FAT.ROOT_DIR_BLOCK;
        else {
            String[] parent = Arrays.copyOf(parts, parts.length - 1);
            dirBlock = resolveDirectoryFirstBlock(parent, false);
            if (dirBlock < 0) { r.put("found", false); return r; }
        }
        DirectoryEntry de = findInDirectory(dirBlock, nm, ext);
        if (de == null) { r.put("found", false); return r; }
        r.put("found", true);
        r.put("content", content);
        r.put("isExecutable", de.isExecutable());
        return r;
    }

    @Override
    public boolean writeOutput(int processId, String content) {
        // 根据课程设计要求，所有可执行程序的结果写入单一 out 文件
        String entry = content == null ? "" : content.trim();
        String existing = "";
        try {
            existing = readFile("out");
        } catch (Exception ignored) {
            existing = "";
        }
        if (existing == null) existing = "";
        StringBuilder sb = new StringBuilder();
        if (!existing.isEmpty()) {
            sb.append(existing);
            if (!existing.endsWith("\n")) sb.append('\n');
        }
        sb.append(entry);
        return createFile("out", sb.toString(), false, true);
    }

    @Override
    public boolean formatDisk() {
        disk.format();
        // recreate FAT
        this.fat = new FAT(disk);
        this.fat.loadFAT();
        return true;
    }

    // Reconcile Disk bitmap with FAT: free any disk blocks that are marked used on Disk but free in FAT
    public void reconcileDiskWithFAT() {
        try {
            boolean[] fatBm = fat.getBitmap();
            boolean[] diskBm = disk.getBitmap();
            if (fatBm == null || diskBm == null) return;
            for (int i = FAT.DATA_START_BLOCK; i < Disk.BLOCK_COUNT; i++) {
                try {
                    if (diskBm[i] && (i < 0 || i >= fatBm.length || !fatBm[i])) {
                        // disk marked used but FAT says free -> free it on disk
                        disk.freeBlock(i);
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
    }

    // Find FAT blocks that are marked used but are not reachable from any directory entry
    // and free them (this handles orphan allocations left after failed operations).
    public void reconcileFATOrphans() {
        try {
            // Collect all blocks that are reachable from directory entries (files and directories)
            java.util.Set<Integer> reachable = new java.util.HashSet<>();
            java.util.Set<Integer> visitedDirs = new java.util.HashSet<>();
            java.util.Deque<Integer> pendingDirs = new java.util.ArrayDeque<>();

            reachable.add(FAT.ROOT_DIR_BLOCK);
            visitedDirs.add(FAT.ROOT_DIR_BLOCK);
            pendingDirs.add(FAT.ROOT_DIR_BLOCK);

            while (!pendingDirs.isEmpty()) {
                int dirBlock = pendingDirs.poll();
                java.util.List<DirectoryEntry> entries = readDirectoryFromBlock(dirBlock);
                if (entries == null) continue;
                for (DirectoryEntry e : entries) {
                    if (e == null) continue;
                    int fb = e.getFirstBlock();
                    if (fb < FAT.DATA_START_BLOCK || fb >= Disk.BLOCK_COUNT) continue;

                    java.util.List<Integer> chain = fat.getFileBlocks(fb);
                    if (chain == null || chain.isEmpty()) {
                        chain = new java.util.ArrayList<>();
                        chain.add(fb);
                    }
                    reachable.addAll(chain);

                    if (e.isDirectory()) {
                        Integer first = chain.get(0);
                        if (first != null && visitedDirs.add(first)) {
                            pendingDirs.add(first);
                        }
                    }
                }
            }

            // Now scan data blocks and free any that are marked used in FAT but not reachable
            for (int i = FAT.DATA_START_BLOCK; i < Disk.BLOCK_COUNT; i++) {
                try {
                    if (!fat.isBlockFree(i) && !reachable.contains(i)) {
                        // free the file chain starting at this block
                        fat.freeFile(i);
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
    }

    @Override
    public Map<String, Object> getDiskStatus() {
        Map<String,Object> m = new HashMap<>();
        // convert DiskSnapshot -> JSON-friendly map
        try {
            Object snapObj = disk.getSnapshot();
            if (snapObj instanceof com.os.filesystem.Disk.DiskSnapshot) {
                com.os.filesystem.Disk.DiskSnapshot snap = (com.os.filesystem.Disk.DiskSnapshot) snapObj;
                Map<String,Object> diskMap = new HashMap<>();
                diskMap.put("freeBlocks", snap.freeBlocks);
                diskMap.put("usedBlocks", snap.usedBlocks);
                // convert boolean[] to List<Boolean>
                boolean[] usage = snap.blockUsage;
                List<Boolean> usageList = new ArrayList<>();
                if (usage != null) {
                    for (boolean b : usage) usageList.add(b);
                }
                diskMap.put("blockUsage", usageList);
                m.put("disk", diskMap);
            } else {
                m.put("disk", snapObj == null ? null : String.valueOf(snapObj));
            }
        } catch (Exception ex) {
            m.put("disk", null);
        }

        // convert FATInfo -> JSON-friendly map
        try {
            Object fiObj = fat.getFATInfo();
            if (fiObj instanceof com.os.filesystem.FAT.FATInfo) {
                com.os.filesystem.FAT.FATInfo fi = (com.os.filesystem.FAT.FATInfo) fiObj;
                Map<String,Object> fatMap = new HashMap<>();
                fatMap.put("totalBlocks", fi.totalBlocks);
                fatMap.put("freeBlocks", fi.freeBlocks);
                fatMap.put("usedBlocks", fi.usedBlocks);
                m.put("fat", fatMap);
            } else {
                m.put("fat", fiObj == null ? null : String.valueOf(fiObj));
            }
        } catch (Exception ex) {
            m.put("fat", null);
        }

        return m;
    }

    @Override
    public Map<String, Object> checkConsistency() {
        return getDiskStatus();
    }

    @Override
    public String[] parsePath(String path) {
        return normalizePathParts(path);
    }

    @Override
    public String normalizePath(String path) {
        if (path == null) return "/";
        if (!path.startsWith("/")) path = "/" + path;
        return path;
    }

    @Override
    public boolean pathExists(String path) {
        String[] parts = normalizePathParts(path);
        if (parts.length == 0) return true;
        // try resolve last component's parent
        if (parts.length == 1) {
            String[] ne = splitNameExt(parts[0]);
            DirectoryEntry e = findInDirectory(FAT.ROOT_DIR_BLOCK, ne[0], ne[1]);
            return e != null;
        }
        String[] parent = Arrays.copyOf(parts, parts.length - 1);
        int parentBlock = resolveDirectoryFirstBlock(parent, false);
        if (parentBlock < 0) return false;
        String[] ne = splitNameExt(parts[parts.length - 1]);
        DirectoryEntry de = findInDirectory(parentBlock, ne[0], ne[1]);
        return de != null;
    }

    @Override
    public boolean isDirectory(String path) {
        String[] parts = normalizePathParts(path);
        if (parts.length == 0) return true;
        String[] parent = Arrays.copyOf(parts, parts.length - 1);
        int parentBlock = parent.length == 0 ? FAT.ROOT_DIR_BLOCK : resolveDirectoryFirstBlock(parent, false);
        if (parentBlock < 0) return false;
        String[] ne = splitNameExt(parts[parts.length - 1]);
        DirectoryEntry de = findInDirectory(parentBlock, ne[0], ne[1]);
        return de != null && de.isDirectory();
    }

    @Override
    public boolean isExecutable(String path) {
        String[] parts = normalizePathParts(path);
        if (parts.length == 0) return false;
        String filename = parts[parts.length - 1];
        String[] ne = splitNameExt(filename);
        String nm = ne[0];
        String ext = ne[1];
        int dirBlock;
        if (parts.length == 1) dirBlock = FAT.ROOT_DIR_BLOCK;
        else {
            String[] parent = Arrays.copyOf(parts, parts.length - 1);
            dirBlock = resolveDirectoryFirstBlock(parent, false);
            if (dirBlock < 0) return false;
        }
        DirectoryEntry de = findInDirectory(dirBlock, nm, ext);
        return de != null && de.isExecutable();
    }

    @Override
    public Map<String, Object> getFileAttributes(String path) {
        Map<String,Object> m = new HashMap<>();
        String[] parts = normalizePathParts(path);
        if (parts.length == 0) return m;
        int dirBlock;
        if (parts.length == 1) dirBlock = FAT.ROOT_DIR_BLOCK;
        else {
            String[] parent = Arrays.copyOf(parts, parts.length - 1);
            dirBlock = resolveDirectoryFirstBlock(parent, false);
            if (dirBlock < 0) return m;
        }
        String filename = parts[parts.length - 1];
        String[] ne = splitNameExt(filename);
        DirectoryEntry de = findInDirectory(dirBlock, ne[0], ne[1]);
        if (de == null) return m;
        m.put("name", de.getName());
        m.put("ext", de.getExtension());
        m.put("attrs", de.getAttributes());
        m.put("firstBlock", de.getFirstBlock());
        int sizeBytes = 0;
        if (!de.isDirectory()) {
            byte[] data = readFileBlocks(de.getFirstBlock());
            sizeBytes = data == null ? 0 : data.length;
        }
        m.put("size", de.getFileSize());
        m.put("sizeBytes", sizeBytes);
        return m;
    }

    @Override
    public int getFileSize(String path) {
        Map<String,Object> a = getFileAttributes(path);
        if (a.containsKey("size")) return (Integer)a.get("size");
        return 0;
    }

    @Override
    public long getModificationTime(String path) {
        return System.currentTimeMillis();
    }

    /**
     * Debug helper: expose underlying Disk instance
     */
    public Disk getDisk() {
        return this.disk;
    }

    /**
     * Debug helper: expose underlying FAT instance
     */
    public FAT getFAT() {
        return this.fat;
    }
}
