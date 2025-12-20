import axios from 'axios';

const api = axios.create({
    baseURL: '/api', // Vite proxy will handle this or direct backend URL
    timeout: 5000,
});

export const kernelApi = {
    // File System
    createFile: (path, content, isExecutable = false, overwrite = false) =>
        api.post(`/fs/create`, content, { params: { path, isExecutable, overwrite } }),

    readFile: (path) =>
        api.get(`/fs/read`, { params: { path } }),

    deleteFile: (path, recursive = false) =>
        api.delete(`/fs/delete`, { params: { path, recursive } }),

    createDir: (path) =>
        api.post(`/fs/mkdir`, null, { params: { path } }),

    listDir: (path = '/') =>
        api.get(`/fs/list`, { params: { path } }),

    copyFile: (src, dst) =>
        api.post(`/fs/copy`, null, { params: { src, dst } }),

    moveFile: (src, dst) =>
        api.post(`/fs/move`, null, { params: { src, dst } }),

    // Process
    runProcess: (path) =>
        api.post(`/process/run`, null, { params: { path } }),

    killProcess: (pid) =>
        api.post(`/process/kill`, null, { params: { pid } }),

    getProcessOutput: (pid) =>
        api.get(`/process/output`, { params: { pid } }),


    // Kernel
    tick: () =>
        api.get(`/kernel/tick`),

    getInfo: () =>
        api.get(`/kernel/info`),

    getDiskStatus: () =>
        api.get(`/disk/status`),

    // Testing: request backend to shutdown gracefully
    shutdown: () => api.get('/shutdown'),

    // Memory
    getMemoryStatus: () => api.get('/memory/status'),
    getProcessMemory: (pid) => api.get(`/memory/process`, { params: { pid } }),
};