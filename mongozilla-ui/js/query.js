function setupQueryTab() {
    const queryConditions = document.getElementById('queryConditions');
    const executeQueryBtn = document.getElementById('executeQueryBtn');
    const queryResults = document.getElementById('queryResults');
    const queryTime = document.getElementById('queryTime');
    const indexUsedName = document.getElementById('indexUsed');

    // Initialize with sample query
    queryConditions.value = JSON.stringify({
        "field": "value"
    }, null, 2);

    executeQueryBtn.onclick = async () => {
        if (!currentCollection) {
            showError('Please select a collection first');
            return;
        }

        try {
            // Parse query conditions
            const conditions = JSON.parse(queryConditions.value);

            console.log(conditions)
            // Execute query
            const startTime = performance.now();
            const response = await queryService.executeQuery(currentCollection, conditions);
            const endTime = performance.now();

            // Update UI
            const { documents, indexUsed, queryTimeMs } = response.data;

            console.log(response.data)
            console.log(indexUsed)
            
            // Display results
            queryResults.textContent = JSON.stringify(documents, null, 2);
            queryTime.textContent = queryTimeMs ;
            indexUsedName.textContent = indexUsed || 'None';

        } catch (error) {
            if (error.name === 'SyntaxError') {
                showError('Invalid JSON format in query conditions');
            } else {
                showError('Failed to execute query');
                console.error('Error executing query:', error);
            }
        }
    };
}

// Add to initialization
document.addEventListener('DOMContentLoaded', () => {
    setupQueryTab();
});
