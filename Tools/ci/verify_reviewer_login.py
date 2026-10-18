"""Exercise the release Android login UI; never save account screens or UI dumps."""
import os
import subprocess

from capture_review_demo import enter_phone, screen, strings, tap

subprocess.run(['adb', 'shell', 'pm', 'clear', 'moe.telecom.arkgram'], check=True)
subprocess.run(['adb', 'shell', 'am', 'start', '-W', '-n',
                'moe.telecom.arkgram/org.telegram.ui.LaunchActivity'], check=True)
tap(screen(None, strings['StartMessaging']), strings['StartMessaging'])
enter_phone('12025550101')
code_page = screen(None, strings['ReviewLoginInfo'])
field = next(node for node in code_page.iter('node') if node.get('class') == 'android.widget.EditText')
tap(field, field.get('text', ''))
subprocess.run(['adb', 'shell', 'input', 'text', os.environ['REVIEW_LOGIN_CODE']], check=True)

chats = screen(None, strings['Settings'])
print('Real Android reviewer sign-in reached the authenticated navigation.', flush=True)
tap(chats, strings['Settings'])
settings = screen(None, strings['AccDescrMoreOptions'])
tap(settings, strings['AccDescrMoreOptions'])
tap(screen(None, strings['LogOut']), strings['LogOut'])
logout = screen(None, strings['LogOutInfo'])
tap(logout, strings['LogOutTitle'])
confirmation = screen(None, strings['AreYouSureLogout'])
# The title and positive button share text; only the button is clickable.
button = next(node for node in confirmation.iter('node')
              if node.get('text', '').casefold() == strings['LogOut'].casefold()
              and node.get('clickable') == 'true')
tap(button, button.attrib['text'])
screen(None, strings['StartMessaging'])
print('The temporary Android review session was logged out.', flush=True)
