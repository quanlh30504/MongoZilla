// Show error message
function showError(message) {
    const errorDiv = document.createElement('div');
    errorDiv.className = 'error-message';
    errorDiv.textContent = message;
    
    const mainContent = document.querySelector('.main-content');
    mainContent.insertBefore(errorDiv, mainContent.firstChild);
    
    setTimeout(() => errorDiv.remove(), 5000);
}

// Format bytes to human readable size
function formatBytes(bytes) {
    if (bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
}

// Switch between tabs
function setupTabs() {
    const tabButtons = document.querySelectorAll('.tab-btn');
    const tabPanels = document.querySelectorAll('.tab-panel');

    tabButtons.forEach(button => {
        button.addEventListener('click', () => {
            const targetTab = button.dataset.tab;
            
            // Update active states
            tabButtons.forEach(btn => btn.classList.remove('active'));
            tabPanels.forEach(panel => panel.classList.remove('active'));
            
            button.classList.add('active');
            document.getElementById(`${targetTab}-view`).classList.add('active');
        });
    });
}

// Show/hide modal
function toggleModal(modalId, show = true) {
    const modal = document.getElementById(modalId);
    if (show) {
        modal.classList.add('active');
    } else {
        modal.classList.remove('active');
    }
}

// Format JSON with proper indentation
function formatJSON(obj) {
    return JSON.stringify(obj, null, 2);
}

// Try to parse JSON, return null if invalid
function tryParseJSON(str) {
    try {
        return JSON.parse(str);
    } catch (e) {
        return null;
    }
}

// Update breadcrumb
function updateBreadcrumb(collection = null) {
    const breadcrumb = document.getElementById('breadcrumb');
    let html = '<span>Databases</span>';
    
    if (collection) {
        html += ' > <span>' + collection + '</span>';
    }
    
    breadcrumb.innerHTML = html;
}
