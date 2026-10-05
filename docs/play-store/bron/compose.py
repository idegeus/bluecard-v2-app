#!/usr/bin/env python3
"""Turns 720x1600 phone captures into 1080x1920 Play Store screenshots: a caption on the felt, the phone below."""
import json, os, subprocess, sys
HERE = os.path.dirname(os.path.abspath(__file__))
CHROME = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
TOP, BOTTOM = 50, 96          # status bar and navigation bar in the raw capture (px)
SCREEN_W = 720                # width of the phone screen in the 1080 px image (1:1, all of it visible)
TEMPLATE = """<!doctype html><html><head><meta charset="utf-8"><style>
html,body{{margin:0;width:1080px;height:1920px;overflow:hidden}}
body{{background:radial-gradient(ellipse 90% 60% at 50% 10%,#1E7A5C 0%,#0F5A43 50%,#0A3D2E 100%);font-family:-apple-system,"SF Pro Display","Helvetica Neue",Arial,sans-serif;color:#fff;text-align:center}}
h1{{margin:0;padding-top:92px;font-size:76px;font-weight:900;letter-spacing:-1px;line-height:1.05;text-shadow:0 4px 18px rgba(0,0,0,.3)}}
p{{margin:22px 70px 0;font-size:38px;line-height:1.3;color:#FFD54F;font-weight:700}}
.phone{{position:absolute;left:{left}px;top:{top}px;width:{w}px;height:{h}px;padding:18px;border-radius:64px;background:#111;box-shadow:0 30px 70px rgba(0,0,0,.5),inset 0 0 0 3px #333}}
.screen{{width:{w}px;height:{h}px;border-radius:46px;overflow:hidden;position:relative;background:#0F5A43}}
.screen img{{position:absolute;left:0;top:-{crop}px;width:{w}px}}
</style></head><body><h1>{title}</h1><p>{sub}</p>
<div class="phone"><div class="screen"><img src="file://{img}"></div></div></body></html>"""

def render(img, title, sub, out):
    scale = SCREEN_W / 720
    h = round((1600 - TOP - BOTTOM) * scale)
    html = TEMPLATE.format(left=(1080 - SCREEN_W - 36) // 2, top=380, w=SCREEN_W, h=h, crop=round(TOP * scale),
                           img=img, title=title, sub=sub)
    page = os.path.join(HERE, "_shot.html")
    open(page, "w").write(html)
    subprocess.run([CHROME, "--headless=new", "--disable-gpu", "--hide-scrollbars", "--force-device-scale-factor=1",
                    "--window-size=1080,1920", "--allow-file-access-from-files", "--screenshot=" + out, "file://" + page],
                   stderr=subprocess.DEVNULL, check=True)

if __name__ == "__main__":
    spec = json.load(open(sys.argv[1]))
    os.makedirs(spec["out"], exist_ok=True)
    for i, s in enumerate(spec["shots"], 1):
        out = os.path.join(spec["out"], "%02d-%s.png" % (i, s["name"]))
        render(s["img"], s["title"], s["sub"], out)
        print(out)
