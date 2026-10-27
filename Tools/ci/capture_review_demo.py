"""Capture the real release UI in CI, using the local demo login number."""
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
        dump = subprocess.run(['adb', 'shell', 'uiautomator', 'dump', '/sdcard/arkgram-window.xml'], check=True,
                              stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
        if b'ERROR: null root node' in dump.stdout:
            print('Waiting for the Android accessibility tree: null root node.', flush=True)
            if time.monotonic() >= deadline:
                raise AssertionError('Android accessibility tree did not become available')
            time.sleep(1)
            continue
        xml = subprocess.check_output(['adb', 'shell', 'cat', '/sdcard/arkgram-window.xml'])
        tree = ET.fromstring(xml)
        if any(node.get('text', '').casefold() == expected.casefold()
               or node.get('content-desc', '').casefold() == expected.casefold() for node in tree.iter('node')):
            break
        if time.monotonic() >= deadline:
            if name:
                (output / (name + '-failed.xml')).write_bytes(xml)
                (output / (name + '-failed.png')).write_bytes(subprocess.check_output(['adb', 'exec-out', 'screencap', '-p']))
                (output / 'crash.log').write_bytes(subprocess.check_output(['adb', 'logcat', '-b', 'crash', '-d']))
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


def enter_phone(number, screenshot=None):
    login = screen(screenshot, strings['PhoneNumber'])
    labels = {node.get('text', '') for node in login.iter('node')}
    assert not labels.intersection({'Reviewer sign-in', strings['ReviewDemoEntry']}), 'Review entries must not be visible'
    for description, value in [(strings['LoginAccessibilityCountryCode'], number[:1]),
                               (strings['PhoneNumber'], number[1:])]:
        field = next(node for node in login.iter('node')
                     if node.get('class') == 'android.widget.EditText' and node.get('content-desc') == description)
        tap(field, field.get('text', ''))
        subprocess.run(['adb', 'shell', 'input', 'keycombination', '113', '29'], check=True)
        subprocess.run(['adb', 'shell', 'input', 'text', value], check=True)
    tap(screen(None, strings['Done']), strings['Done'])


if __name__ == '__main__':
    subprocess.run(['adb', 'shell', 'wm', 'size', '1080x1920'], check=True)
    subprocess.run(['adb', 'shell', 'wm', 'density', '420'], check=True)
    subprocess.run(['adb', 'shell', 'am', 'start', '-W', '-n',
                    'moe.telecom.arkgram/org.telegram.ui.LaunchActivity'], check=True)
    welcome = screen('01-welcome', strings['StartMessaging'])
    tap(welcome, strings['StartMessaging'])
    enter_phone('12025550100', '02-login')
    chats = screen('03-offline-demo', strings['ReviewDemoTitle'])
    chat_tab = next(node for node in chats.iter('node') if node.get('content-desc') == strings['MainTabsChats'])
    settings_tab = next(node for node in chats.iter('node') if node.get('content-desc') == strings['Settings'])
    chat_bounds = list(map(int, re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', chat_tab.attrib['bounds']).groups()))
    settings_bounds = list(map(int, re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', settings_tab.attrib['bounds']).groups()))
    subprocess.run(['adb', 'shell', 'input', 'swipe', str((chat_bounds[0] + chat_bounds[2]) // 2),
                    str((chat_bounds[1] + chat_bounds[3]) // 2), str((settings_bounds[0] + settings_bounds[2]) // 2),
                    str((settings_bounds[1] + settings_bounds[3]) // 2), '300'], check=True)
    settings = screen('03-demo-settings-drag', strings['FlexLlmSettings'])
    tap(settings, strings['MainTabsContacts'])
    contacts = screen('03-demo-contacts', strings['ReviewDemoContactOne'])
    tap(contacts, strings['ReviewDemoContactOne'])
    screen('04-sample-conversation', strings['ReviewDemoContactOne'])
    subprocess.run(['adb', 'shell', 'input', 'keyevent', '4'], check=True)
    chats = screen('05-demo-tabs', strings['Settings'])
    tap(chats, strings['Settings'])
    screen('06-demo-settings', strings['FlexLlmSettings'])
