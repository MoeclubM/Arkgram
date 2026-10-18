"""CI probe of Telegram's documented test DC; never connects to production.

No account is registered and no messages are sent. A signup-required response
proves the fixed code was accepted, but is not reported as an authorized account.
Session keys stay in memory and are never included in the result artifact.
"""
import asyncio
import json
import os
import re
from pathlib import Path

from telethon import TelegramClient, functions, types
from telethon.errors import RPCError

source = Path('TMessagesProj/src/main/java/org/telegram/messenger/BuildVars.java').read_text(encoding='utf-8')
api_id = int(re.search(r'APP_ID\s*=\s*(\d+)', source).group(1))
api_hash = re.search(r'APP_HASH\s*=\s*"([^"]+)"', source).group(1)
dc = int(os.environ['TEST_DC'])
address = {1: '149.154.175.40', 2: '149.154.167.40', 3: '149.154.175.117'}[dc]
phone = f'99966{dc}' + os.environ['GITHUB_RUN_ID'][-4:]
result = {'test_dc': dc, 'address': address, 'port': 80,
          'phone': phone, 'status': 'connecting', 'account_created': False,
          'attempt_code_length': int(os.environ['TEST_CODE_LENGTH']),
          'documentation': 'https://core.telegram.org/api/auth#test-accounts'}


async def main():
    client = TelegramClient(None, api_id, api_hash)
    client.session.set_dc(dc, address, 80)
    try:
        await client.connect()
        config = await client(functions.help.GetConfigRequest())
        assert config.test_mode, 'The endpoint must identify itself as a Telegram test server'
        result['server_test_mode'] = config.test_mode
        result['status'] = 'requesting_test_code'
        sent = await client(functions.auth.SendCodeRequest(
            phone_number=phone, api_id=api_id, api_hash=api_hash, settings=types.CodeSettings()))
        result['code_type'] = type(sent.type).__name__
        result['code_length'] = sent.type.length
        result['status'] = 'verifying_fixed_code'
        authorization = await client(functions.auth.SignInRequest(
            phone_number=phone, phone_code_hash=sent.phone_code_hash,
            phone_code=str(dc) * result['attempt_code_length']))
        result['fixed_code_accepted'] = True
        result['authorization_type'] = type(authorization).__name__
        if isinstance(authorization, types.auth.AuthorizationSignUpRequired):
            result['status'] = 'fixed_code_accepted_signup_required'
            result['terms_of_service_present'] = authorization.terms_of_service is not None
        else:
            user = await client.get_me()
            assert user.id == authorization.user.id
            result['status'] = 'authorized_with_fixed_code'
            await client(functions.auth.LogOutRequest())
    except RPCError as error:
        result['failed_stage'] = result['status']
        result['status'] = 'failed'
        result['rpc_error'] = {'type': type(error).__name__, 'code': error.code,
                               'message': error.message}
        raise
    finally:
        await client.disconnect()
        Path('telegram-test-server-result.json').write_text(
            json.dumps(result, indent=2) + '\n', encoding='utf-8')
        print(json.dumps(result, indent=2))


asyncio.run(main())
