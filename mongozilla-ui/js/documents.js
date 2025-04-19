let selectedDocument = null;
let currentPage = 0;
let pageSize = 10;
let totalPages = 0;

async function loadDocuments() {
    if (!currentCollection) return;

    try {
        const response = await documentService.getDocuments(currentCollection, currentPage, pageSize);
        const { content: documents, totalElements, totalPages: total, hasNext, hasPrevious } = response.data;
        
        totalPages = total;
        
        const container = document.getElementById('documents-list');
        container.innerHTML = '';
        
        // Add documents
        documents.forEach(doc => {
            const div = document.createElement('div');
            div.className = `document-item ${doc._id === selectedDocument?._id ? 'selected' : ''}`;
            div.innerHTML = `<pre>${formatJSON(doc)}</pre>`;
            div.onclick = () => selectDocument(doc);
            container.appendChild(div);
        });

        // Update pagination controls
        updatePaginationControls(hasNext, hasPrevious, currentPage + 1, totalPages, totalElements);
    } catch (error) {
        showError('Failed to load documents');
        console.error('Error loading documents:', error);
    }
}

function updatePaginationControls(hasNext, hasPrevious, currentPageNum, totalPages, totalItems) {
    const paginationContainer = document.getElementById('pagination-controls');
    paginationContainer.innerHTML = `
        <div class="pagination-info">
            Showing page ${currentPageNum} of ${totalPages} (${totalItems} items)
        </div>
        <div class="pagination-buttons">
            <button ${!hasPrevious ? 'disabled' : ''} onclick="changePage(${currentPage - 1})">
                Previous
            </button>
            <button ${!hasNext ? 'disabled' : ''} onclick="changePage(${currentPage + 1})">
                Next
            </button>
        </div>
    `;
}

function changePage(newPage) {
    currentPage = newPage;
    loadDocuments();
}

function selectDocument(doc) {
    selectedDocument = doc;
    const editor = document.getElementById('documentContent');
    editor.value = formatJSON(doc);
    loadDocuments(); // Refresh list to update selected state
}

function setupDocumentActions() {
    const addBtn = document.getElementById('addDocumentBtn');
    const saveBtn = document.getElementById('saveDocumentBtn');
    const editor = document.getElementById('documentContent');
    const searchInput = document.getElementById('documentSearch');
    const pageSizeSelect = document.getElementById('pageSize');

    addBtn.onclick = () => {
        selectedDocument = {};
        editor.value = '{\n  \n}';
        loadDocuments();
    };

    saveBtn.onclick = async () => {
        const content = editor.value;
        const parsedContent = tryParseJSON(content);
        
        if (!parsedContent) {
            showError('Invalid JSON format');
            return;
        }

        try {
            if (selectedDocument._id) {
                await documentService.updateDocument(
                    currentCollection,
                    selectedDocument._id,
                    parsedContent
                );
            } else {
                await documentService.createDocument(
                    currentCollection,
                    parsedContent
                );
            }
            loadDocuments();
        } catch (error) {
            showError('Failed to save document');
            console.error('Error saving document:', error);
        }
    };

    // Handle page size changes
    pageSizeSelect.onchange = () => {
        pageSize = parseInt(pageSizeSelect.value);
        currentPage = 0; // Reset to first page
        loadDocuments();
    };

    // Simple document filtering
    searchInput.onkeyup = () => {
        const searchTerm = searchInput.value.toLowerCase();
        const items = document.querySelectorAll('.document-item');
        
        items.forEach(item => {
            const text = item.textContent.toLowerCase();
            item.style.display = text.includes(searchTerm) ? 'block' : 'none';
        });
    };
}
