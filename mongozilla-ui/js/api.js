const API_BASE_URL = 'http://localhost:8080/api';

const api = axios.create({
    baseURL: API_BASE_URL,
    headers: {
        'Content-Type': 'application/json',
    },
});

const queryService = {
    executeQuery: (collection, conditions) =>
        api.post(`/collections/query?collectionName=${collection}`, conditions)
}


const collectionService = {
    getAllCollections: () => api.get('/collections'),
    createCollection: (name) => api.post('/collections', { name }),
    deleteCollection: (name) => api.delete(`/collections/${name}`),
};

const documentService = {
    getDocuments: (collection, page = 0, size = 10, sortBy = '_id', sortDirection = 'asc') => 
        api.get(`/documents/${collection}`, {
            params: { page, size, sortBy, sortDirection }
        }),
    createDocument: (collection, document) => 
        api.post(`/documents/${collection}`, document),
    updateDocument: (collection, id, document) => 
        api.put(`/documents/${collection}/${id}`, document),
    deleteDocument: (collection, id) => 
        api.delete(`/documents/${collection}/${id}`),
};

const indexService = {
    getIndexes: (collection) => api.get(`/collections/indexes`, {
        params: { collectionName : collection }
    }),
    createIndex: (collection, fields, includeFullData) => api.post(`/collections/create-index`, null, {
        params: {
            collectionName: collection,
            indexFields: fields,
            includeFullData: includeFullData
        }
    }),
    deleteIndex: (collection, indexName) => api.delete(`/collections/${collection}/indexes/${indexName}`),
    
};
