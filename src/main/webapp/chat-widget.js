
(function () {

    if (document.getElementById("hc-chat-launcher")) {
        return;
    }

    // Chatbot styling
    var style = document.createElement("style");

    style.textContent =
        "#hc-chat-launcher {" +
        "position:fixed;right:22px;bottom:22px;z-index:9997;" +
        "border:1px solid #e5b869;" +
        "background:linear-gradient(120deg,#e5b869,#e06d53);" +
        "color:#120d08;border-radius:999px;padding:13px 18px;" +
        "font-weight:800;box-shadow:0 10px 35px #0008;cursor:pointer;}" +

        "#hc-chat-panel {" +
        "position:fixed;right:20px;bottom:82px;" +
        "width:min(360px,calc(100vw - 30px));height:440px;" +
        "max-height:70vh;z-index:9997;background:#14100d;" +
        "border:1px solid #80613a;border-radius:20px;" +
        "box-shadow:0 22px 60px #000b;display:none;" +
        "overflow:hidden;color:#f7f3ea;font:14px system-ui;}" +

        "#hc-chat-panel.open {" +
        "display:flex;flex-direction:column;}" +

        "#hc-chat-head {" +
        "padding:14px 16px;background:#211911;" +
        "color:#e5b869;font-weight:800;" +
        "display:flex;justify-content:space-between;}" +

        "#hc-chat-messages {" +
        "padding:14px;overflow:auto;flex:1;}" +

        "#hc-chat-messages p {" +
        "padding:10px 12px;border-radius:12px;" +
        "background:#282017;margin:8px 0;" +
        "max-width:92%;white-space:pre-wrap;}" +

        "#hc-chat-messages p.user {" +
        "margin-left:auto;background:#60472b;color:white;}" +

        "#hc-chat-form {" +
        "display:flex;padding:10px;gap:7px;" +
        "border-top:1px solid #5a4328;}" +

        "#hc-chat-input {" +
        "min-width:0;flex:1;padding:10px;background:#090807;" +
        "color:white;border:1px solid #665033;border-radius:10px;}" +

        "#hc-chat-form button {" +
        "border:0;background:#e5b869;border-radius:10px;" +
        "padding:0 14px;font-weight:800;cursor:pointer;}" +

        "#hc-chat-cats {" +
        "font-size:11px;color:#b7a58c;padding:0 14px 12px;}";

    document.head.appendChild(style);

    // Create chatbot launcher
    var launcher = document.createElement("button");

    launcher.id = "hc-chat-launcher";
    launcher.textContent = "✦ Craft Assistant";

    document.body.appendChild(launcher);

    // Create chatbot panel
    var panel = document.createElement("section");

    panel.id = "hc-chat-panel";

    panel.innerHTML =
        '<div id="hc-chat-head">' +
            '<span>Handcraft Guide</span>' +
            '<button type="button" id="hc-chat-close" ' +
            'style="background:none;border:0;color:#e5b869;font-size:20px;">×</button>' +
        '</div>' +

        '<div id="hc-chat-messages">' +
            '<p>Hello! Ask me about craft categories, products, sellers, delivery, orders or payments.</p>' +
        '</div>' +

        '<div id="hc-chat-cats">Categories: loading...</div>' +

        '<form id="hc-chat-form">' +
            '<input id="hc-chat-input" placeholder="Ask about handmade crafts..." autocomplete="off">' +
            '<button type="submit">Send</button>' +
        '</form>';

    document.body.appendChild(panel);

    var messages = panel.querySelector("#hc-chat-messages");
    var categories = panel.querySelector("#hc-chat-cats");
    var input = panel.querySelector("#hc-chat-input");
    var form = panel.querySelector("#hc-chat-form");

    // Open chatbot
    launcher.addEventListener("click", function () {
        panel.classList.toggle("open");
    });

    // Close chatbot
    panel.querySelector("#hc-chat-close")
        .addEventListener("click", function () {
            panel.classList.remove("open");
        });

    // API request helper
    function sendRequest(method, body, callback) {

        var xhr = new XMLHttpRequest();

        xhr.open(method, "api/chatbot", true);

        if (method === "POST") {
            xhr.setRequestHeader(
                "Content-Type",
                "application/json"
            );
        }

        xhr.onreadystatechange = function () {

            if (xhr.readyState !== 4) {
                return;
            }

            var data;

            try {
                data = JSON.parse(xhr.responseText);
            } catch (error) {
                callback(new Error("Invalid server response."));
                return;
            }

            if (xhr.status >= 200 && xhr.status < 300) {
                callback(null, data);
            } else {
                callback(new Error(
                    data.error || "Chatbot request failed."
                ));
            }
        };

        xhr.onerror = function () {
            callback(new Error("Unable to connect to chatbot service."));
        };

        xhr.send(body ? JSON.stringify(body) : null);
    }

    // Load categories
    sendRequest("GET", null, function (error, data) {

        if (error) {
            categories.textContent =
                "Explore all handmade craft categories";
            return;
        }

        categories.textContent =
            "Explore: " + (data.categories || []).join(" · ");
    });

    // Send customer message
    form.addEventListener("submit", function (event) {

        event.preventDefault();

        var message = input.value.trim();

        if (!message) {
            return;
        }

        var userMessage = document.createElement("p");

        userMessage.className = "user";
        userMessage.textContent = message;

        messages.appendChild(userMessage);

        input.value = "";

        var wait = document.createElement("p");

        wait.textContent = "Finding an answer...";

        messages.appendChild(wait);
        messages.scrollTop = messages.scrollHeight;

        sendRequest("POST", { message: message }, function (error, data) {

            if (error) {
                wait.textContent = error.message;
            } else {
                wait.textContent =
                    data.answer || data.error ||
                    "I could not find an answer.";
            }

            messages.scrollTop = messages.scrollHeight;
        });

    })

})();
