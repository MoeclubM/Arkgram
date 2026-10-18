"""Approve real Telegram login tokens for one explicitly configured review account."""
import base64
import binascii
import hmac
import json
import logging
import os
from pathlib import Path

from aiohttp import BasicAuth, web
from telethon import TelegramClient, functions
from telethon.errors import RPCError, SessionPasswordNeededError

ROOT = Path(__file__).parent
CLIENT = web.AppKey('client', TelegramClient)
CODE_HASH = web.AppKey('phone_code_hash', str)


def create_app():
    phone = os.environ['TG_PHONE']
    owner_auth = BasicAuth('owner', os.environ['OWNER_PASSWORD']).encode()
    review_auth = BasicAuth('reviewer', os.environ['REVIEW_PASSWORD']).encode()
    review_bearer = 'Bearer ' + os.environ['REVIEW_PASSWORD']
    login_bearer = 'Bearer ' + os.environ['REVIEW_LOGIN_CODE']
    login_account = os.environ['REVIEW_LOGIN_ACCOUNT']

    @web.middleware
    async def authenticate(request, handler):
        if request.path != '/healthz':
            supplied = request.headers.get('Authorization', '').encode()
            expected = owner_auth if request.path.startswith('/admin/') else review_auth
            allowed = hmac.compare_digest(supplied, expected.encode())
            if request.path == '/api/login':
                allowed = hmac.compare_digest(supplied, login_bearer.encode())
            elif not request.path.startswith('/admin/'):
                allowed |= hmac.compare_digest(supplied, review_bearer.encode())
            if not allowed:
                raise web.HTTPUnauthorized(headers={'WWW-Authenticate': 'Basic realm="ArkGram review"'})
        if request.method == 'POST':
            if request.content_type != 'application/json':
                raise web.HTTPUnsupportedMediaType(text='Send application/json.')
            try:
                request['payload'] = await request.json()
            except json.JSONDecodeError:
                raise web.HTTPBadRequest(text='Invalid JSON.')
            if not isinstance(request['payload'], dict):
                raise web.HTTPBadRequest(text='Send a JSON object.')
        try:
            response = await handler(request)
        except RPCError as error:
            logging.exception('Telegram rejected %s', request.path)
            response = web.json_response({'error': type(error).__name__, 'message': str(error)}, status=502)
        response.headers['Cache-Control'] = 'no-store'
        response.headers['X-Content-Type-Options'] = 'nosniff'
        return response

    app = web.Application(middlewares=[authenticate])
    app[CLIENT] = TelegramClient(
        os.environ['TG_SESSION'], int(os.environ['TG_API_ID']), os.environ['TG_API_HASH'],
        device_model='ArkGram review authorization service', flood_sleep_threshold=0,
        raise_last_call_error=True)

    async def session(application):
        await application[CLIENT].connect()
        try:
            yield
        finally:
            await application[CLIENT].disconnect()

    app.cleanup_ctx.append(session)

    async def account_status(request):
        client = request.app[CLIENT]
        user = await client.get_me()
        if user is None:
            return web.json_response({'authorized': False, 'message': 'Owner must sign in first.'}, status=503)
        if user.phone != phone:
            await client.log_out()
            raise web.HTTPConflict(text='Signed-in account does not match TG_PHONE. The session was revoked.')
        return web.json_response({'authorized': True, 'phone': '+' + user.phone})

    async def health(request):
        connected = request.app[CLIENT].is_connected()
        return web.json_response({'telegram_connected': connected}, status=200 if connected else 503)

    async def page(request):
        name = 'admin.html' if request.path == '/admin/' else 'review.html'
        return web.Response(text=(ROOT / name).read_text(encoding='utf-8'), content_type='text/html')

    async def send_code(request):
        client = request.app[CLIENT]
        if await client.get_me() is not None:
            raise web.HTTPConflict(text='Already signed in. Use the account status endpoint.')
        sent = await client.send_code_request(phone)
        request.app[CODE_HASH] = sent.phone_code_hash
        return web.json_response({'code_sent': True, 'delivery': type(sent.type).__name__,
                                  'phone': '+' + phone})

    async def sign_in(request):
        client = request.app[CLIENT]
        data = request['payload']
        if not isinstance(data.get('code'), str) or not data['code'].strip():
            raise web.HTTPBadRequest(text='A Telegram login code is required.')
        if CODE_HASH not in request.app:
            raise web.HTTPConflict(text='Request a login code first; a restart clears the pending login.')
        try:
            await client.sign_in(phone=phone, code=data['code'].strip(), phone_code_hash=request.app[CODE_HASH])
        except SessionPasswordNeededError:
            return web.json_response({'password_required': True})
        return await account_status(request)

    async def password(request):
        data = request['payload']
        if not isinstance(data.get('password'), str) or not data['password']:
            raise web.HTTPBadRequest(text='The Telegram two-step verification password is required.')
        await request.app[CLIENT].sign_in(password=data['password'])
        return await account_status(request)

    async def authorize(request):
        data = request['payload']
        if request.path == '/api/login' and data.get('account') != login_account:
            raise web.HTTPUnauthorized(text='Invalid review account.')
        raw = data.get('token')
        if not isinstance(raw, str) or not raw.strip():
            raise web.HTTPBadRequest(text='A Telegram QR login token is required.')
        raw = raw.strip().removeprefix('tg://login?token=')
        try:
            token = base64.b64decode(raw + '=' * (-len(raw) % 4), altchars=b'-_', validate=True)
        except (ValueError, binascii.Error):
            raise web.HTTPBadRequest(text='The token must use base64url encoding.')
        status = await account_status(request)
        if status.status != 200:
            return status
        authorization = await request.app[CLIENT](functions.auth.AcceptLoginTokenRequest(token))
        logging.info('Approved Telegram device authorization %s', authorization.hash)
        return web.json_response({'authorized': True, 'authorization_hash': str(authorization.hash)})

    app.add_routes([
        web.get('/healthz', health), web.get('/', page), web.get('/admin/', page),
        web.get('/admin/status', account_status), web.get('/api/status', account_status),
        web.post('/admin/send-code', send_code), web.post('/admin/sign-in', sign_in),
        web.post('/admin/password', password), web.post('/api/authorize', authorize),
        web.post('/api/login', authorize),
    ])
    return app


if __name__ == '__main__':
    logging.basicConfig(level=logging.INFO)
    web.run_app(create_app(), host='127.0.0.1', port=int(os.environ['PORT']))
