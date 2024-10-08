const socket = new WebSocket("ws://localhost:7777/canvas");
const startURL = "http://localhost:63342/DuoCanvas/src/main/resources/public/canvas.html"

let lastDrawTime = null;
let selectedColor = "#000"; // Save selected color
let canvasCode = new URLSearchParams(document.location.search).get("canvasCode");

const interval = 15000;
let pingInterval;
let isCanvasLoaded = false;
let colorPickerOpen = false;
let lastX;
let lastY;

const canvasContainer = document.getElementById("canvas-container");
const preLoader = document.getElementById("preloader_img");
const selectedPixelImage = document.getElementById("selected_pixel")
const canvas = document.getElementById("canvas");
const footer = document.querySelector(".footer-container");
const colorSelector = document.querySelectorAll('input[name="radio-control"]');
const xy_display = document.getElementById("xy_display");
const placeButton = document.getElementById("place_button");
const placeButtonText = document.getElementById("place_text");

/* Time Checker */

const timeChecker = setInterval(function () {

    if (lastDrawTime == null) {
        return;
    }

    let currentTime = new Date().getTime();
    let timeDifference = (lastDrawTime + 300000) - currentTime;

    if (timeDifference <= 0) {
        placeButtonText.innerText = "Place!";
        placeButton.style.backgroundColor = "#d73a00";
        placeButton.disabled = false;
        return;
    }

    let minutes = Math.floor((timeDifference % (1000 * 60 * 60)) / (1000 * 60));
    let seconds = Math.floor((timeDifference % (1000 * 60)) / 1000);

    minutes = minutes < 10 ? '0' + minutes : minutes;
    seconds = seconds < 10 ? '0' + seconds : seconds;

    placeButtonText.innerText = minutes + " : " + seconds;
    placeButton.style.backgroundColor = "#6d6e6d";
    placeButton.disabled = true;


}, 1000);

/* Handlers*/

socket.onopen = function () {
    console.log("Connected to Canvas: " + canvasCode);

    pingInterval = setInterval(() => {
        if (socket.readyState === WebSocket.OPEN) {
            socket.send("0");
        }
    }, interval);

};

socket.onclose = function () {

    clearInterval(pingInterval);
    console.log("Disconnected from Canvas: " + canvasCode);
};

socket.onerror = function (error) {
    console.log("Canvas-socket Error: " + error);
};

socket.onmessage = function (event) {

    let jsonData = JSON.parse(event.data);
    let type = jsonData.type;

    if (type === null) {
        return;
    }

    switch (type) {
        /* Initializes a session, if not connecting to an existing one.*/
        case "sessionResponse":

            canvasCode = jsonData.canvasCode;

            if (canvasCode == null) {
                return;
            }

            let url = new URL(startURL);
            let params = new URLSearchParams(url.search);

            params.set("canvasCode", canvasCode);
            url.search = params.toString();

            window.location.href = url.toString();

            break;
        /* Receives canvas that needs to be drawn*/
        case "canvasResponse":
            loadCanvas(jsonData);
            break;
        /* Updates the canvas, whenever a new pixel is placed.*/
        case "canvasUpdate":
            drawRect(
                jsonData.x
                , jsonData.y,
                jsonData.color
            );
            break;
        case "imageResponse":
            downloadFile(jsonData.image).then(r => console.log("Downloading image..."));
            break;
    }
}

/* Functions used by the handlers */

function sendImageRequest() {
    const imageRequest = {
        requestType: "image",
        canvasCode: canvasCode,
    };
    socket.send(JSON.stringify(imageRequest))
}

function sendDrawRequest(userid, color, x, y) {

    if (!canDrawAgain()) {
        console.log("Draw request failed: Cooldown is still active.");
        return false;
    }

    const drawRequest = {
        requestType: "draw",
        canvasCode: canvasCode,
        color: color,
        x: x,
        y: y,
        date: Math.floor(new Date().getTime() / (1000 * 60))
    };

    socket.send(JSON.stringify(drawRequest))
    showFooter(false);
    lastDrawTime = new Date().getTime();
}

function sendCanvasRequest() {
    const canvasRequest = {
        requestType: "canvas",
        canvasCode: canvasCode,
    };
    socket.send(JSON.stringify(canvasRequest))
}

function createNewSession() {
    const request = {
        requestType: "session",
        canvasCode: null,
    };
    socket.send(JSON.stringify(request));
}

function canDrawAgain() {
    return lastDrawTime === null || (new Date().getTime() - lastDrawTime) >= 300000;
}

function showFooter(show = true) {
    if (!isCanvasLoaded) {
        return;
    }
    if (show && !colorPickerOpen) {
        placeButton.style.opacity = "0"
        placeButton.style.pointerEvents = "none"
        new Audio("sounds/select_position.mp3").play()
        footer.classList.add('active');
        colorPickerOpen = true;
    } else {
        setTimeout(function () {
            placeButton.style.opacity = "1";
            placeButton.style.pointerEvents = "auto"
        }, 300);
        footer.classList.remove('active')
        new Audio("sounds/cancel.mp3").play();
        colorPickerOpen = false;
    }

}

function loadCanvas(canvasData) {
    const positions = JSON.parse(canvasData.canvasData);

    try {
        for (let i = 0; i < positions.length; i++) {

            let x = positions[i]["position"]["x"];
            let y = positions[i]["position"]["y"];
            let color = positions[i]["color"];

            drawRect(x, y, color);
        }
        preLoader.style.display = "none";
        canvasContainer.style.display = "fixed";
        selectedPixelImage.style.display = "block";
        selectedPixelImage.style.left = (800 - 25 / 2) + 'px';
        selectedPixelImage.style.top = (800 - 25 / 2) + 'px';
        placeButton.style.display = "block";

        isCanvasLoaded = true;
    } catch (e) {
        console.log("Could not load pixel: " + e);
    }
}

function drawRect(x, y, color) {
    ctx.fillStyle = color
    ctx.fillRect(x, y, 1, 1)
}

/* Canvas */

let ctx = canvas.getContext('2d')

canvas.addEventListener('click', function (e) {
    if (!isDragging && isCanvasLoaded) {
        const rect = canvas.getBoundingClientRect();

        const elementRelativeX = e.clientX - rect.left;
        const elementRelativeY = e.clientY - rect.top;

        const canvasRelativeX = elementRelativeX * (canvas.width / rect.width);
        const canvasRelativeY = elementRelativeY * (canvas.height / rect.height);

        // 25 = 25px image size
        selectedPixelImage.style.left = (rect.left + elementRelativeX - 25 / 2) + 'px';
        selectedPixelImage.style.top = (rect.top + elementRelativeY - 25 / 2) + 'px';

        lastX = Math.round(canvasRelativeX);
        lastY = Math.round(canvasRelativeY);

        xy_display.innerText = `(${lastX},${lastY})`
    }
});

let scaleFactor = 1;
let initialOffsetX = 0;
let initialOffsetY = 0;

let isZooming = false;
let targetScaleFactor;

canvasContainer.onwheel = zoom;

function zoom(event) {
    event.preventDefault();

    targetScaleFactor = scaleFactor + event.deltaY * -0.01;
    targetScaleFactor = Math.min(Math.max(1, targetScaleFactor), 10);

    isZooming = true;
    requestAnimationFrame(zoomAnimation);
}

function zoomAnimation() {
    if (!isZooming) return;

    scaleFactor += (targetScaleFactor - scaleFactor) * 0.1;

    canvasContainer.style.transform = `scale(${scaleFactor}) translate(${initialOffsetX}px, ${initialOffsetY}px)`;

    if (Math.abs(targetScaleFactor - scaleFactor) > 0.01) {
        requestAnimationFrame(zoomAnimation);
    } else {
        isZooming = false;
    }
}

async function downloadFile(file) {
    const image = new Image();
    image.src = 'data:image/png;base64,' + file;

    let a = document.createElement('a');
    a.download = canvasCode + 'png';
    a.href = image.src;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
}

let isDragging = false;
let startX, startY;
let offsetX = 0, offsetY = 0;

canvasContainer.addEventListener('mousedown', (e) => {
    isDragging = true;
    startX = e.clientX;
    startY = e.clientY;
});

document.addEventListener('mousemove', (e) => {
    if (!isDragging) return;

    offsetX = (e.clientX - startX) / scaleFactor;
    offsetY = (e.clientY - startY) / scaleFactor;

    const deadZone = 10;
    if (Math.abs(offsetX) < deadZone && Math.abs(offsetY) < deadZone) {
        return;
    }

    canvasContainer.style.transform = `scale(${scaleFactor}) translate(${initialOffsetX + offsetX}px, ${initialOffsetY + offsetY}px)`;
});

document.addEventListener('mouseup', () => {
    isDragging = false;

    initialOffsetX += offsetX;
    initialOffsetY += offsetY;
});


colorSelector.forEach(radio => {
    radio.addEventListener('click', (e) => {
        const label = e.target.closest('.radio-button-label');
        const swatch = label.querySelector('.swatch');

        selectedColor = swatch.dataset.color;
        new Audio("sounds/switch_color.mp3").play();
    });
});

window.addEventListener('load', function () {
    if (canvasCode != null) {
        setTimeout(function () {
            if (socket.readyState === WebSocket.OPEN) {
                sendCanvasRequest();
                console.log("CanvasRequest sent.")
            }
        }, 2500);
    }
})


