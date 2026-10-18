"""CI-only real device login round trip; no chats are fetched or messages sent."""
import asyncio
import json
import os
import re
import urllib.request
from pathlib import Path

from telethon import TelegramClient, functions

source = Path('TMessagesProj/src/main/java/org/telegram/messenger/BuildVars.java').read_text(encoding='utf-8')
api_id = int(re.search(r'APP_ID\s*=\s*(\d+)', source).group(1))
api_hash = re.search(r'APP_HASH\s*=\s*"([^"]+)"', source).group(1)
base = os.environ['REVIEW_DEPLOYMENT_URL']
headers = {'Authorization': 'Bearer ' + os.environ['REVIEW_ACCESS_CODE'].strip()}


async def main():
    with urllib.request.urlopen(urllib.request.Request(base + 'api/status', headers=headers)) as response:
        account = json.load(response)
    assert account['authorized'] is True
    client = TelegramClient(None, api_id, api_hash, device_model='ArkGram CI authorization verification')
    try:
        await client.connect()
        qr = await client.qr_login()
        waiting = asyncio.create_task(qr.wait())
        await asyncio.sleep(0)  # Register the update handler before accepting the token.
        body = json.dumps({'account': '12025550101', 'token': qr.url}).encode()
        request = urllib.request.Request(base + 'api/login', data=body,
                                         headers={'Authorization': 'Bearer ' + os.environ['REVIEW_LOGIN_CODE'],
                                                  'Content-Type': 'application/json'})
        response = await asyncio.to_thread(urllib.request.urlopen, request)
        with response:
            result = json.load(response)
        assert result['authorized'] is True
        print('The deployed service accepted a real Telegram login token.', flush=True)
        user = await waiting
        assert '+' + user.phone == account['phone'], 'The new session must match the configured account'
        print('Review account Telegram Premium access:', bool(user.premium), flush=True)
        await client(functions.auth.LogOutRequest())
        print('Real Telegram device login verified; the temporary CI session was logged out.', flush=True)
    finally:
        await client.disconnect()


asyncio.run(main())
