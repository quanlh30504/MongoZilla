// Initialize the application
document.addEventListener('DOMContentLoaded', () => {
    // Setup UI components
    setupTabs();
    setupCollectionCreation();
    setupDocumentActions();
    setupIndexActions();

    // Load initial data
    loadCollections();

    // Handle modal background clicks
    document.querySelectorAll('.modal').forEach(modal => {
        modal.addEventListener('click', (e) => {
            if (e.target === modal) {
                toggleModal(modal.id, false);
            }
        });
    });
});
