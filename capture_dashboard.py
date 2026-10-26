import subprocess
import time
import json
import base64
import requests
import asyncio
import websockets

async def capture():
    # Start Chrome with remote debugging
    chrome_proc = subprocess.Popen([
        r"C:\Program Files\Google\Chrome\Application\chrome.exe",
        "--headless=new",
        "--remote-debugging-port=9222",
        "--disable-gpu",
        "--window-size=1440,920",
        "about:blank"
    ])
    
    try:
        # Wait for Chrome to be ready
        for _ in range(20):
            try:
                r = requests.get("http://localhost:9222/json")
                if r.status_code == 200 and len(r.json()) > 0:
                    ws_url = r.json()[0]['webSocketDebuggerUrl']
                    break
            except Exception:
                time.sleep(0.3)
        else:
            raise Exception("Could not connect to Chrome debugging port")

        async with websockets.connect(ws_url) as ws:
            async def send(method, params=None):
                msg_id = int(time.time() * 1000) % 1000000
                await ws.send(json.dumps({"id": msg_id, "method": method, "params": params or {}}))
                while True:
                    resp = json.loads(await ws.recv())
                    if resp.get("id") == msg_id:
                        return resp.get("result", {})

            # Navigate to login page
            await send("Page.navigate", {"url": "http://localhost:8080/"})
            await asyncio.sleep(1.5)

            # Evaluate login credentials and submit
            login_js = """
            (() => {
                document.getElementById('login-username').value = 'student01';
                document.getElementById('login-password').value = 'student123';
                document.getElementById('btn-submit-login').click();
            })();
            """
            await send("Runtime.evaluate", {"expression": login_js})
            await asyncio.sleep(2.0)

            # Take screenshot
            res = await send("Page.captureScreenshot", {"format": "png"})
            img_data = base64.b64decode(res['data'])
            with open("C:/Users/SANTHOSH/.college/project/scratch/course_registration_system/web/dashboard_capture.png", "wb") as f:
                f.write(img_data)
            print("Successfully captured dashboard screenshot!")
    finally:
        chrome_proc.terminate()
        chrome_proc.wait()

if __name__ == "__main__":
    asyncio.run(capture())
