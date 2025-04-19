let currentCollection = null;

async function loadCollections() {
    try {
        const response = await collectionService.getAllCollections();
        const collections = response.data;
        
        const container = document.getElementById('collections-container');
        container.innerHTML = '';
        
        collections.forEach(collection => {
            const div = document.createElement('div');
            div.className = `collection-item ${collection.name === currentCollection ? 'active' : ''}`;
            div.textContent = collection.name;
            div.onclick = () => selectCollection(collection.name);
            container.appendChild(div);
        });
    } catch (error) {
        showError('Failed to load collections');
        console.error('Error loading collections:', error);
    }
}

function selectCollection(name) {
    currentCollection = name;
    updateBreadcrumb(name);
    loadCollections(); // Refresh list to update active state
    loadDocuments();
    loadIndexes();
}

function setupCollectionCreation() {
    const createBtn = document.getElementById('createCollectionBtn');
    const modal = document.getElementById('createCollectionModal');
    const cancelBtn = document.getElementById('cancelCreateCollection');
    const confirmBtn = document.getElementById('confirmCreateCollection');
    const input = document.getElementById('newCollectionName');

    createBtn.onclick = () => toggleModal('createCollectionModal', true);
    cancelBtn.onclick = () => toggleModal('createCollectionModal', false);

    confirmBtn.onclick = async () => {
        const name = input.value.trim();
        if (!name) {
            showError('Collection name is required');
            return;
        }

        try {
            await collectionService.createCollection(name);
            toggleModal('createCollectionModal', false);
            input.value = '';
            loadCollections();
        } catch (error) {
            showError('Failed to create collection');
            console.error('Error creating collection:', error);
        }
    };
}

// Setup refresh button
document.getElementById('refreshBtn').onclick = loadCollections;
