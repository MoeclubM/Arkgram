"""Capture the real release UI in CI, using the explicit local demo entry."""
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path

output = Path('play-screenshots')
output.mkdir(exist_ok=True)
strings = {item.attrib['name']: ''.join(item.itertext()) for item in ET.parse(
    'TMessagesProj/src/main/res/values/strings.xml').getroot() if item.tag == 'string'}


def screen(name, expected):
    deadline = time.monotonic() + 90
    while True:
        subprocess.run(['adb', 'shell', 'uiautomator', 'dump', '/sdcard/arkgram-window.xml'], check=True,
                       stdout=subprocess.DEVNULL)
        xml = subprocess.check_output(['adb', 'shell', 'cat', '/sdcard/arkgram-window.xml'])
        tree = ET.fromstring(xml)
        if any(node.get('text', '').casefold() == expected.casefold()
               or node.get('content-desc', '').casefold() == expected.casefold() for node in tree.iter('node')):
            break
        if time.monotonic() >= deadline:
            raise AssertionError('UI did not reach: ' + expected)
        time.sleep(1)
    if name:
        (output / (name + '.xml')).write_bytes(xml)
        (output / (name + '.png')).write_bytes(subprocess.check_output(['adb', 'exec-out', 'screencap', '-p']))
    return tree


def tap(tree, label):
    print('Tap:', label, flush=True)
    node = next(node for node in tree.iter('node')
                if node.get('text', '').casefold() == label.casefold()
                or node.get('content-desc', '').casefold() == label.casefold())
    left, top, right, bottom = map(int, re.fullmatch(
        r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', node.attrib['bounds']).groups())
    subprocess.run(['adb', 'shell', 'input', 'tap', str((left + right) // 2), str((top + bottom) // 2)], check=True)


if __name__ == '__main__':
    subprocess.run(['adb', 'shell', 'wm', 'size', '1080x1920'], check=True)
    subprocess.run(['adb', 'shell', 'wm', 'density', '420'], check=True)
    subprocess.run(['adb', 'shell', 'am', 'start', '-W', '-n',
                    'moe.telecom.arkgram/org.telegram.ui.LaunchActivity'], check=True)
    welcome = screen('01-welcome', strings['StartMessaging'])
    tap(welcome, strings['StartMessaging'])
    login = screen('02-login', strings['ReviewDemoEntry'])
    tap(login, strings['ReviewDemoEntry'])
    chats = screen('03-offline-demo', strings['ReviewDemoContactOne'])
    tap(chats, strings['ReviewDemoContactOne'])
    screen('04-sample-conversation', strings['ReviewDemoContactOne'])
    subprocess.run(['adb', 'shell', 'input', 'keyevent', '4'], check=True)
    chats = screen('05-demo-tabs', strings['Settings'])
    tap(chats, strings['Settings'])
    screen('06-demo-settings', strings['FlexLlmSettings'])
