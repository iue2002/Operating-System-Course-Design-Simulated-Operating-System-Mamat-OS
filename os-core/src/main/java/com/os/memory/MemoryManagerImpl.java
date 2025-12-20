package com.os.memory;

import com.os.core.interfaces.MemoryManagerAPI;
import com.os.core.models.PageTableEntry;

import java.util.*;

/**
 * 简单的分页内存管理实现（满足课程设计需求的最小实现）
 * - 默认物理页框数：32
 * - 页大小（bytes）：256
 *
 * 功能：创建/销毁页表；分配/释放页；逻辑地址到物理地址转换；空闲页位图管理；简单的统计。
 */
public class MemoryManagerImpl implements MemoryManagerAPI {
    private final int TOTAL_PAGES = 32; // 物理页框数
    private final int PAGE_SIZE = 256;  // 每页字节数
    private final int SYSTEM_PAGE_COUNT = 10; // 系统区页数（满足最多10个PCB）

    // 空闲页位图：true 表示空闲
    private final boolean[] freePageBitmap;
    // 为简单起见，用链表维护空闲页列表
    private final LinkedList<Integer> freePages;

    // 每个进程的页表：pid -> List<PageTableEntry>
    private final Map<Integer, List<PageTableEntry>> pageTables;

    // 统计
    private final Map<Integer, Integer> allocationCounts;
    private final List<Integer> systemPages;
    private final Map<Integer, String> systemPageOwners;
    private final java.util.Deque<Integer> freeSystemPages;
    private final Map<Integer, Integer> systemPageAssignments; // system page -> pid (-1 表示空闲/OS)
    private final Map<Integer, Integer> pidSystemPages;        // pid -> system page

    public MemoryManagerImpl() {
        freePageBitmap = new boolean[TOTAL_PAGES];
        freePages = new LinkedList<>();
        systemPages = new ArrayList<>();
        systemPageOwners = new HashMap<>();
        freeSystemPages = new ArrayDeque<>();
        systemPageAssignments = new HashMap<>();
        pidSystemPages = new HashMap<>();
        for (int i = 0; i < TOTAL_PAGES; i++) {
            if (i < SYSTEM_PAGE_COUNT) {
                freePageBitmap[i] = false;
                systemPages.add(i);
                systemPageOwners.put(i, "OS");
                freeSystemPages.addLast(i);
                systemPageAssignments.put(i, -1);
            } else {
                freePageBitmap[i] = true;
                freePages.add(i);
            }
        }
        pageTables = new HashMap<>();
        allocationCounts = new HashMap<>();
    }

    // ========== 内存分配 ==========
    @Override
    public synchronized Map<String, Object> allocatePages(int pid, int pageCount, List<Integer> logicalPages) {
        Map<String,Object> r = new HashMap<>();
        if (!hasEnoughMemory(pageCount)) {
            r.put("success", false);
            r.put("allocated", 0);
            return r;
        }
        List<PageTableEntry> pt = pageTables.computeIfAbsent(pid, k -> new ArrayList<>());
        int allocated = 0;
        for (int i = 0; i < pageCount; i++) {
            int phys = allocateFreePage();
            if (phys < 0) break;
            int logical = (logicalPages != null && i < logicalPages.size()) ? logicalPages.get(i) : findFirstAvailableIndex(pt);
            // ensure page table size
            while (pt.size() <= logical) pt.add(new PageTableEntry());
            PageTableEntry entry = new PageTableEntry(logical, phys);
            pt.set(logical, entry);
            allocated++;
        }
        allocationCounts.put(pid, allocationCounts.getOrDefault(pid,0) + allocated);
        r.put("success", allocated == pageCount);
        r.put("allocated", allocated);
        return r;
    }

    @Override
    public synchronized int freeMemory(int pid, List<Integer> logicalPages) {
        List<PageTableEntry> pt = pageTables.get(pid);
        if (pt == null) return 0;
        int freed = 0;
        if (logicalPages == null) {
            // release all
            for (PageTableEntry e : pt) {
                if (e != null && e.isValid()) {
                    releaseFreePage(e.getPhysicalPage());
                    freed++;
                }
            }
            pageTables.remove(pid);
        } else {
            for (Integer lp : logicalPages) {
                if (lp < 0 || lp >= pt.size()) continue;
                PageTableEntry e = pt.get(lp);
                if (e != null && e.isValid()) {
                    releaseFreePage(e.getPhysicalPage());
                    e.setValid(false);
                    freed++;
                }
            }
        }
        allocationCounts.put(pid, Math.max(0, allocationCounts.getOrDefault(pid,0) - freed));
        return freed;
    }

    @Override
    public synchronized int allocatePage(int pid, int logicalPage) {
        if (!hasEnoughMemory(1)) return -1;
        int phys = allocateFreePage();
        if (phys < 0) return -1;
        List<PageTableEntry> pt = pageTables.computeIfAbsent(pid, k -> new ArrayList<>());
        while (pt.size() <= logicalPage) pt.add(new PageTableEntry());
        PageTableEntry entry = new PageTableEntry(logicalPage, phys);
        pt.set(logicalPage, entry);
        allocationCounts.put(pid, allocationCounts.getOrDefault(pid,0)+1);
        return phys;
    }

    private int findFirstAvailableIndex(List<PageTableEntry> pageTable) {
        for (int i = 0; i < pageTable.size(); i++) {
            PageTableEntry e = pageTable.get(i);
            if (e == null || !e.isValid()) return i;
        }
        return pageTable.size();
    }

    @Override
    public synchronized boolean freePage(int pid, int logicalPage) {
        List<PageTableEntry> pt = pageTables.get(pid);
        if (pt == null) return false;
        if (logicalPage < 0 || logicalPage >= pt.size()) return false;
        PageTableEntry e = pt.get(logicalPage);
        if (e == null || !e.isValid()) return false;
        releaseFreePage(e.getPhysicalPage());
        e.setValid(false);
        allocationCounts.put(pid, Math.max(0, allocationCounts.getOrDefault(pid,0)-1));
        return true;
    }

    // ========== 地址转换 ==========
    @Override
    public synchronized Map<String, Object> translateAddress(int pid, int logicalAddress) {
        Map<String,Object> r = new HashMap<>();
        int[] dec = decomposeAddress(logicalAddress);
        int page = dec[0];
        int offset = dec[1];
        PageTableEntry e = getPageTableEntry(pid, page);
        if (e == null || !e.isValid()) {
            r.put("success", false);
            r.put("reason", "page fault or invalid page");
            return r;
        }
        int physical = composeAddress(e.getPhysicalPage(), offset);
        r.put("success", true);
        r.put("physicalAddress", physical);
        r.put("physicalPage", e.getPhysicalPage());
        r.put("offset", offset);
        return r;
    }

    @Override
    public synchronized boolean isValidAddress(int pid, int logicalAddress) {
        int[] dec = decomposeAddress(logicalAddress);
        int page = dec[0];
        PageTableEntry e = getPageTableEntry(pid, page);
        return e != null && e.isValid();
    }

    @Override
    public synchronized int getPhysicalAddress(int pid, int logicalAddress) {
        Map<String,Object> t = translateAddress(pid, logicalAddress);
        if (Boolean.TRUE.equals(t.get("success"))) {
            return (Integer)t.get("physicalAddress");
        }
        return -1;
    }

    // ========== 页表管理 ==========
    @Override
    public synchronized int createPageTable(int pid, int pageCount) {
        List<PageTableEntry> pt = pageTables.computeIfAbsent(pid, k -> new ArrayList<>());
        // ensure size
        while (pt.size() < pageCount) pt.add(new PageTableEntry());
        return 0; // 返回页表位置的占位值（内存模拟，不使用真实地址）
    }

    @Override
    public synchronized boolean destroyPageTable(int pid) {
        List<PageTableEntry> pt = pageTables.remove(pid);
        if (pt == null) return false;
        // release any allocated pages
        for (PageTableEntry e : pt) {
            if (e != null && e.isValid()) releaseFreePage(e.getPhysicalPage());
        }
        allocationCounts.remove(pid);
        return true;
    }

    @Override
    public synchronized List<PageTableEntry> getPageTable(int pid) {
        List<PageTableEntry> pt = pageTables.get(pid);
        if (pt == null) return new ArrayList<>();
        return new ArrayList<>(pt);
    }

    @Override
    public synchronized boolean updatePageTableEntry(int pid, int logicalPage, PageTableEntry entry) {
        List<PageTableEntry> pt = pageTables.get(pid);
        if (pt == null) return false;
        while (pt.size() <= logicalPage) pt.add(new PageTableEntry());
        pt.set(logicalPage, entry);
        return true;
    }

    @Override
    public synchronized PageTableEntry getPageTableEntry(int pid, int logicalPage) {
        List<PageTableEntry> pt = pageTables.get(pid);
        if (pt == null) return null;
        if (logicalPage < 0 || logicalPage >= pt.size()) return null;
        return pt.get(logicalPage);
    }

    // ========== 内存状态查询 ==========
    @Override
    public synchronized Map<String, Object> getMemoryStatus() {
        Map<String,Object> s = new HashMap<>();
        int free = freePages.size();
        int used = TOTAL_PAGES - free;
        double usage = TOTAL_PAGES == 0 ? 0.0 : (used * 100.0) / TOTAL_PAGES;
        int userTotal = TOTAL_PAGES - SYSTEM_PAGE_COUNT;
        int userUsed = Math.max(0, used - SYSTEM_PAGE_COUNT);
        double userUsage = userTotal <= 0 ? 0.0 : (userUsed * 100.0) / userTotal;
        s.put("totalPages", TOTAL_PAGES);
        s.put("pageSize", PAGE_SIZE);
        s.put("freePagesCount", free);
        s.put("usedPagesCount", used);
        s.put("usagePercentage", usage);
        s.put("systemPageCount", SYSTEM_PAGE_COUNT);
        s.put("systemPages", new ArrayList<>(systemPages));
        s.put("systemPageOwners", new HashMap<>(systemPageOwners));
        int systemFree = freeSystemPages.size();
        int systemUsed = SYSTEM_PAGE_COUNT - systemFree;
        double systemUsage = SYSTEM_PAGE_COUNT == 0 ? 0.0 : (systemUsed * 100.0) / SYSTEM_PAGE_COUNT;
        s.put("systemFreePagesCount", systemFree);
        s.put("systemUsedPagesCount", systemUsed);
        s.put("systemUsagePercentage", systemUsage);
        s.put("userTotalPages", userTotal);
        s.put("userUsedPagesCount", userUsed);
        s.put("userUsagePercentage", userUsage);
        List<Integer> freeList = new ArrayList<>(freePages);
        Collections.sort(freeList);
        s.put("freePages", freeList);
        s.put("allocations", new HashMap<>(allocationCounts));
        Map<Integer,Integer> owners = new HashMap<>();
        for (Map.Entry<Integer, List<PageTableEntry>> entry : pageTables.entrySet()) {
            int pid = entry.getKey();
            List<PageTableEntry> tables = entry.getValue();
            if (tables == null) continue;
            for (PageTableEntry pte : tables) {
                if (pte != null && pte.isValid()) {
                    owners.put(pte.getPhysicalPage(), pid);
                }
            }
        }
        for (Integer sysPage : systemPages) {
            Integer ownerPid = systemPageAssignments.getOrDefault(sysPage, -1);
            owners.put(sysPage, ownerPid == null ? -1 : ownerPid);
        }
        s.put("pageOwners", owners);
        return s;
    }

    @Override
    public synchronized List<Integer> getFreePages() {
        return new ArrayList<>(freePages);
    }

    @Override
    public synchronized Map<String, Object> getProcessMemoryUsage(int pid) {
        Map<String,Object> m = new HashMap<>();
        m.put("allocatedPages", allocationCounts.getOrDefault(pid,0));
        m.put("pageTable", getPageTable(pid));
        return m;
    }

    @Override
    public synchronized boolean hasEnoughMemory(int requiredPages) {
        return freePages.size() >= requiredPages;
    }

    @Override
    public synchronized Map<String, Object> getFragmentationInfo() {
        Map<String,Object> m = new HashMap<>();
        m.put("freePages", freePages.size());
        return m;
    }

    // ========== 空闲页管理 ==========
    @Override
    public synchronized int allocateFreePage() {
        if (freePages.isEmpty()) return -1;
        int p = freePages.removeFirst();
        freePageBitmap[p] = false;
        return p;
    }

    @Override
    public synchronized boolean releaseFreePage(int physicalPage) {
        if (physicalPage < 0 || physicalPage >= TOTAL_PAGES) return false;
        if (isSystemPage(physicalPage)) return false;
        if (freePageBitmap[physicalPage]) return false; // already free
        freePageBitmap[physicalPage] = true;
        freePages.addLast(physicalPage);
        return true;
    }

    @Override
    public synchronized int findConsecutiveFreePages(int count) {
        if (count <= 0) return -1;
        int consecutive = 0;
        for (int i = 0; i < TOTAL_PAGES; i++) {
            if (freePageBitmap[i]) consecutive++; else consecutive = 0;
            if (consecutive >= count) return i - count + 1;
        }
        return -1;
    }

    @Override
    public synchronized void updateFreePageBitmap(int physicalPage, boolean allocated) {
        if (physicalPage < 0 || physicalPage >= TOTAL_PAGES) return;
        if (isSystemPage(physicalPage)) return;
        freePageBitmap[physicalPage] = !allocated;
        if (allocated) freePages.remove((Integer)physicalPage);
        else if (!freePages.contains(physicalPage)) freePages.add(physicalPage);
    }

    // ========== TLB管理（简化） ==========
    @Override
    public synchronized int lookupTLB(int pid, int logicalPage) {
        return -1; // 未实现 TLB
    }

    @Override
    public synchronized boolean updateTLB(int pid, int logicalPage, int physicalPage) {
        return false;
    }

    @Override
    public synchronized void clearTLB() {
    }

    @Override
    public synchronized double getTLBHitRate() {
        return 0.0;
    }

    // ========== 系统管理 ==========
    @Override
    public synchronized boolean initialize() {
        // already initialized in constructor
        return true;
    }

    @Override
    public synchronized boolean reset() {
        freePages.clear();
        systemPages.clear();
        systemPageOwners.clear();
        freeSystemPages.clear();
        systemPageAssignments.clear();
        pidSystemPages.clear();
        for (int i = 0; i < TOTAL_PAGES; i++) {
            if (i < SYSTEM_PAGE_COUNT) {
                freePageBitmap[i] = false;
                systemPages.add(i);
                systemPageOwners.put(i, "OS");
                freeSystemPages.addLast(i);
                systemPageAssignments.put(i, -1);
            } else {
                freePageBitmap[i] = true;
                freePages.add(i);
            }
        }
        pageTables.clear();
        allocationCounts.clear();
        return true;
    }

    @Override
    public synchronized int allocateSystemPage(int pid) {
        if (pid <= 0) return -1;
        if (pidSystemPages.containsKey(pid)) {
            return pidSystemPages.get(pid);
        }
        Integer page = freeSystemPages.pollFirst();
        if (page == null) return -1;
        pidSystemPages.put(pid, page);
        systemPageAssignments.put(page, pid);
        systemPageOwners.put(page, "PID " + pid);
        return page;
    }

    @Override
    public synchronized boolean releaseSystemPage(int pid) {
        Integer page = pidSystemPages.remove(pid);
        if (page == null) return false;
        systemPageAssignments.put(page, -1);
        systemPageOwners.put(page, "OS");
        if (!freeSystemPages.contains(page)) {
            freeSystemPages.addLast(page);
        }
        return true;
    }

    @Override
    public synchronized int getFreeSystemPageCount() {
        return freeSystemPages.size();
    }

    @Override
    public synchronized Map<String, Object> checkMemoryConsistency() {
        return getMemoryStatus();
    }

    @Override
    public synchronized Map<String, Object> getMemoryConfig() {
        Map<String,Object> c = new HashMap<>();
        c.put("totalPages", TOTAL_PAGES);
        c.put("pageSize", PAGE_SIZE);
        return c;
    }

    // ========== 性能统计 ==========
    @Override
    public synchronized Map<String, Object> getAllocationStatistics() {
        return getMemoryStatus();
    }

    @Override
    public synchronized Map<String, Object> getTLBStatistics() {
        Map<String,Object> m = new HashMap<>();
        m.put("hitRate", getTLBHitRate());
        return m;
    }

    @Override
    public synchronized Map<String, Object> getPerformanceMetrics() {
        Map<String,Object> m = new HashMap<>();
        m.put("allocations", new HashMap<>(allocationCounts));
        return m;
    }

    // ========== 工具方法 ==========
    @Override
    public int[] decomposeAddress(int logicalAddress) {
        int page = logicalAddress / PAGE_SIZE;
        int offset = logicalAddress % PAGE_SIZE;
        return new int[]{page, offset};
    }

    @Override
    public int composeAddress(int physicalPage, int offset) {
        return physicalPage * PAGE_SIZE + offset;
    }

    @Override
    public boolean isValidPageNumber(int pageNumber) {
        return pageNumber >= 0 && pageNumber < TOTAL_PAGES;
    }

    @Override
    public boolean isValidOffset(int offset) {
        return offset >= 0 && offset < PAGE_SIZE;
    }

    private boolean isSystemPage(int pageNumber) {
        return pageNumber >= 0 && pageNumber < SYSTEM_PAGE_COUNT;
    }
}
