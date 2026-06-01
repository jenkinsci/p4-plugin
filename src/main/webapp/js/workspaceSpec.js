(function() {
    function validatePollingPath() {
        var textarea = document.getElementById("id.pollPath");
        var warningDiv = document.getElementById("pollPathWarning");
        if (!textarea || !warningDiv) return;

        var entries = textarea.value.split(',')
            .map(function(e) { return e.trim(); })
            .filter(function(e) { return e.length > 0; });

        if (entries.length > 10) {
            textarea.style.border = "2px solid red";
            warningDiv.textContent = "Maximum 10 comma-separated entries are allowed in this field. Example: //depot/1, //depot/2, ..., //depot/10";
        } else {
            textarea.style.border = "";
            warningDiv.textContent = "";
        }
    }

    function togglePollPath() {
        var checkbox = document.getElementById("id.customPolling");
        var entry = document.getElementById("pollPathEntry");
        if (!checkbox || !entry) return;
        entry.style.display = checkbox.checked ? "" : "none";
    }

    function validateOnSubmit() {
        var checkbox = document.getElementById("id.customPolling");
        var textarea = document.getElementById("id.pollPath");
        var warningDiv = document.getElementById("pollPathWarning");
        if (checkbox && checkbox.checked && textarea && textarea.value.trim() === '') {
            if (warningDiv) {
                warningDiv.textContent = "At least one polling path is required when Custom Polling is enabled.";
            }
            textarea.style.border = "2px solid red";
            textarea.focus();
            return false;
        }
        return true;
    }

    // Elements are already in the DOM — run immediately to set initial state
    togglePollPath();

    var checkbox = document.getElementById("id.customPolling");
    var textarea = document.getElementById("id.pollPath");
    if (checkbox) {
        checkbox.addEventListener("change", togglePollPath);
    }
    if (textarea) {
        textarea.addEventListener("input", validatePollingPath);
    }

    // Hook into Save and Apply buttons
    var form = checkbox ? checkbox.closest("form") : null;
    if (form) {
        form.addEventListener("submit", function(e) {
            if (!validateOnSubmit()) {
                e.preventDefault();
                e.stopImmediatePropagation();
            }
        });
    }
    // Jenkins "Apply" button does not trigger form submit — intercept click
    document.querySelectorAll("button[name='Apply'], input[name='Apply']").forEach(function(btn) {
        btn.addEventListener("click", function(e) {
            if (!validateOnSubmit()) {
                e.preventDefault();
                e.stopImmediatePropagation();
            }
        });
    });
})();
