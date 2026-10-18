"""CI-only HTTP checks against a real, unauthorized Telegram connection."""
import base64
import os
import re
import sys
import tempfile
import unittest
from pathlib import Path

from aiohttp import BasicAuth
from aiohttp.test_utils import TestClient, TestServer

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from server import create_app


class ReviewServiceTests(unittest.IsolatedAsyncioTestCase):
    async def asyncSetUp(self):
        source = (Path(__file__).resolve().parents[3] /
                  'TMessagesProj/src/main/java/org/telegram/messenger/BuildVars.java').read_text(encoding='utf-8')
        self.directory = tempfile.TemporaryDirectory()
        os.environ.update({
            'TG_API_ID': re.search(r'APP_ID\s*=\s*(\d+)', source).group(1),
            'TG_API_HASH': re.search(r'APP_HASH\s*=\s*"([^"]+)"', source).group(1),
            'TG_PHONE': '9996620000',
            'TG_SESSION': str(Path(self.directory.name) / 'unauthorized'),
            'OWNER_PASSWORD': 'ci-owner-only', 'REVIEW_PASSWORD': 'ci-reviewer-only',
            'REVIEW_LOGIN_ACCOUNT': '12025550101', 'REVIEW_LOGIN_CODE': '012345678901',
        })
        self.client = TestClient(TestServer(create_app()))
        await self.client.start_server()
        self.owner = BasicAuth('owner', 'ci-owner-only')
        self.reviewer = BasicAuth('reviewer', 'ci-reviewer-only')

    async def asyncTearDown(self):
        await self.client.close()
        self.directory.cleanup()

    async def test_every_account_route_requires_credentials(self):
        for method, path in [('get', '/'), ('get', '/api/status'), ('get', '/admin/'),
                             ('get', '/admin/status'), ('post', '/api/authorize'), ('post', '/api/login'),
                             ('post', '/admin/send-code'), ('post', '/admin/sign-in'),
                             ('post', '/admin/password')]:
            response = await getattr(self.client, method)(path)
            self.assertEqual(response.status, 401, path)

    async def test_owner_and_reviewer_credentials_are_separate(self):
        response = await self.client.get('/admin/', auth=self.owner)
        self.assertEqual(response.status, 200)
        self.assertIn('Set up the review account', await response.text())
        response = await self.client.get('/admin/', auth=self.reviewer)
        self.assertEqual(response.status, 401)
        response = await self.client.get('/', auth=self.owner)
        self.assertEqual(response.status, 401)
        response = await self.client.get('/', auth=self.reviewer)
        self.assertEqual(response.status, 200)
        self.assertEqual(response.headers['Cache-Control'], 'no-store')

    async def test_connection_health_does_not_claim_account_login(self):
        response = await self.client.get('/healthz')
        self.assertEqual(response.status, 200)
        self.assertEqual(await response.json(), {'telegram_connected': True})
        response = await self.client.get('/api/status', headers={'Authorization': 'Bearer ci-reviewer-only'})
        self.assertEqual(response.status, 503)
        self.assertFalse((await response.json())['authorized'])

    async def test_invalid_input_and_cross_site_forms_are_rejected(self):
        response = await self.client.post('/api/authorize', auth=self.reviewer, data={'token': 'abc'})
        self.assertEqual(response.status, 415)
        for payload in [[], {}, {'token': ''}, {'token': 42}, {'token': '!not-base64!'}]:
            response = await self.client.post('/api/authorize', auth=self.reviewer, json=payload)
            self.assertEqual(response.status, 400, payload)
        response = await self.client.post('/api/authorize', auth=self.reviewer,
                                          data='{', headers={'Content-Type': 'application/json'})
        self.assertEqual(response.status, 400)
        response = await self.client.post('/admin/sign-in', auth=self.owner, json={'code': '12345'})
        self.assertEqual(response.status, 409)
        response = await self.client.post('/admin/password', auth=self.owner, json={})
        self.assertEqual(response.status, 400)

    async def test_uninitialized_account_never_authorizes_a_token(self):
        token = base64.urlsafe_b64encode(b'not-an-issued-login-token').decode().rstrip('=')
        for raw in [token, 'tg://login?token=' + token]:
            response = await self.client.post('/api/authorize', auth=self.reviewer, json={'token': raw})
            self.assertEqual(response.status, 503)
            self.assertFalse((await response.json())['authorized'])

    async def test_login_requires_both_review_account_and_private_code(self):
        token = base64.urlsafe_b64encode(b'not-an-issued-login-token').decode().rstrip('=')
        for authorization in ['Bearer wrong-code', 'Bearer ci-reviewer-only', self.reviewer.encode()]:
            response = await self.client.post('/api/login', headers={'Authorization': authorization},
                                              json={'account': '12025550101', 'token': token})
            self.assertEqual(response.status, 401)
        headers = {'Authorization': 'Bearer 012345678901'}
        for payload in [{}, {'account': '9996620000', 'token': token}, {'account': 12025550101, 'token': token}]:
            response = await self.client.post('/api/login', headers=headers, json=payload)
            self.assertEqual(response.status, 401)
        response = await self.client.post('/api/status', headers=headers)
        self.assertEqual(response.status, 401)
        response = await self.client.post('/api/login', headers=headers,
                                          json={'account': '12025550101', 'token': token})
        self.assertEqual(response.status, 503)
        self.assertFalse((await response.json())['authorized'])
