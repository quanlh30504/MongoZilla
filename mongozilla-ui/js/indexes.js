async function loadIndexes() {
    if (!currentCollection) return;

    try {
        const response = await indexService.getIndexes(currentCollection);
        const indexes = response.data;

        const container = document.getElementById('indexes-list');
        container.innerHTML = '';

        if (indexes.length === 0) {
            const tr = document.createElement('tr');
            tr.innerHTML = '<td colspan="5" class="empty-message">No indexes found. Create one using the "Create Index" button.</td>';
            container.appendChild(tr);
            return;
        }

        indexes.forEach(index => {
            const tr = document.createElement('tr');
            tr.innerHTML = `
                <td>${index.indexName}</td>
                <td>${index.fields.join(', ')}</td>
                <td>${index.includeFullData ? 'Yes' : 'No'}</td>
                <td>${formatBytes(index.sizeBytes)}</td>
                <td>
                    <button class="delete-btn" onclick="deleteIndex('${index.indexName}')">Delete</button>
                </td>
            `;
            container.appendChild(tr);
        });
    } catch (error) {
        showError('Failed to load indexes');
        console.error('Error loading indexes:', error);
    }
}

async function deleteIndex(name) {
    if (!confirm('Are you sure you want to delete this index?')) {
        return;
    }

    try {
        await indexService.deleteIndex(currentCollection, name);
        loadIndexes();
    } catch (error) {
        showError('Failed to delete index');
        console.error('Error deleting index:', error);
    }
}

function setupIndexActions() {
    const createBtn = document.getElementById('createIndexBtn');
    const modal = document.getElementById('createIndexModal');
    const cancelBtn = document.getElementById('cancelCreateIndex');
    const confirmBtn = document.getElementById('confirmCreateIndex');
    const fieldsInput = document.getElementById('indexFields');
    const includeFullDataCheckbox = document.getElementById('includeFullData');
    
    // Open modal when Create Index button is clicked
    createBtn.onclick = () => {
        if (!currentCollection) {
            showError('Please select a collection first');
            return;
        }
        
        // Reset form
        fieldsInput.value = '';
        includeFullDataCheckbox.checked = true;
        
        // Show modal
        modal.classList.add('active');
    };
    
    // Close modal when Cancel button is clicked
    cancelBtn.onclick = () => {
        modal.classList.remove('active');
    };
    
    // Create index when Confirm button is clicked
    confirmBtn.onclick = async () => {
        const fields = fieldsInput.value.trim();
        const includeFullData = includeFullDataCheckbox.checked;
        
        if (!fields) {
            showError('Please specify at least one field to index');
            return;
        }
        
        try {
            // Show loading state
            confirmBtn.disabled = true;
            confirmBtn.textContent = 'Creating...';
            
            // Call API to create index
            await indexService.createIndex(currentCollection, fields, includeFullData);
            
            // Close modal and refresh index list
            modal.classList.remove('active');
            loadIndexes();
            
            // Show success message
            showSuccess('Index created successfully');
        } catch (error) {
            showError('Failed to create index: ' + (error.response?.data || error.message));
            console.error('Error creating index:', error);
        } finally {
            // Reset button state
            confirmBtn.disabled = false;
            confirmBtn.textContent = 'Create Index';
        }
    };
    
    // Close modal when clicking outside
    window.onclick = (event) => {
        if (event.target === modal) {
            modal.classList.remove('active');
        }
    };
}
function formatBytes(bytes) {
    if (bytes === 0) return '0 Bytes';
    const k = 1024;
    const sizes = ['Bytes', 'KB', 'MB', 'GB', 'TB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
}


