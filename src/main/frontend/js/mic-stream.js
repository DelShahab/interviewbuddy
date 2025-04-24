window.micStreamer = (() => {
  let socket;
  let mediaRecorder;

  const setMicStatus = (emoji, text, color) => {
    const el = document.querySelector("#micStatus");
    if (el) {
      el.textContent = `${emoji} Mic: ${text}`;
      el.style.color = color;
    }
  };

  const start = async () => {
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });

      mediaRecorder = new MediaRecorder(stream, {
        mimeType: "audio/webm"
      });

      socket = new WebSocket("ws://localhost:8080/ws/audio");
      socket.binaryType = "arraybuffer";

      socket.onopen = () => {
        mediaRecorder.ondataavailable = (event) => {
          if (event.data.size > 0 && socket.readyState === WebSocket.OPEN) {
            event.data.arrayBuffer().then(buffer => {
              socket.send(buffer);
            });
          }
        };

        mediaRecorder.start(250);
        setMicStatus("🟢", "Active", "green");
        console.log("🎙️ Mic recording started");
      };

      socket.onclose = () => {
        console.warn("🔌 WebSocket closed");
        setMicStatus("🔴", "Inactive", "red");
      };

    } catch (err) {
      console.error("❌ Mic access failed:", err);
      setMicStatus("🔴", "Error", "darkred");
    }
  };

  const stop = () => {
    if (mediaRecorder && mediaRecorder.state !== "inactive") {
      mediaRecorder.stop();
      console.log("🛑 MediaRecorder stopped.");
    }

    if (mediaRecorder && mediaRecorder.stream) {
      mediaRecorder.stream.getTracks().forEach(track => track.stop());
      console.log("🔇 Mic input tracks stopped.");
    }

    if (socket && socket.readyState === WebSocket.OPEN) {
      socket.close();
      console.log("🔌 WebSocket closed.");
    }

    setMicStatus("🔴", "Inactive", "red");
  };

  return { start, stop };
})();
