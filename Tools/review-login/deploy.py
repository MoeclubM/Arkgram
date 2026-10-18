"""Deploy the CI-verified service using D:/NET's existing fleet SSH client."""
import argparse
import json
import re
import secrets
import shlex
import sys
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument('--fleet-root', type=Path, default=Path('D:/NET'))
parser.add_argument('--phone', required=True, help='International phone number, digits only')
parser.add_argument('--wheel-dir', type=Path, required=True, help='Pure Python wheels downloaded from the verified CI run')
args = parser.parse_args()
sys.path.insert(0, str(args.fleet_root))
from connect import get_ssh_client

source_root = Path(__file__).resolve().parent
build_vars = (source_root.parents[1] /
              'TMessagesProj/src/main/java/org/telegram/messenger/BuildVars.java').read_text(encoding='utf-8')
credentials_path = args.fleet_root / 'credentials/arkgram-review-oracle-osa.json'
if credentials_path.exists():
    credentials = json.loads(credentials_path.read_text(encoding='utf-8'))
    if credentials['phone'] != args.phone:
        raise ValueError('Stored account differs from --phone; resolve the configuration explicitly.')
else:
    credentials = {
        'phone': args.phone,
        'base_url': 'https://secapi.telecom.moe/arkgram-review/',
        'owner_username': 'owner', 'owner_password': secrets.token_urlsafe(32),
        'review_username': 'reviewer', 'review_password': secrets.token_urlsafe(32),
    }
    credentials_path.write_text(json.dumps(credentials, indent=2) + '\n', encoding='utf-8')

environment = {
    'TG_API_ID': re.search(r'APP_ID\s*=\s*(\d+)', build_vars).group(1),
    'TG_API_HASH': re.search(r'APP_HASH\s*=\s*"([^"]+)"', build_vars).group(1),
    'TG_PHONE': args.phone, 'TG_SESSION': '/var/lib/arkgram-review/telegram',
    'OWNER_PASSWORD': credentials['owner_password'], 'REVIEW_PASSWORD': credentials['review_password'],
    'PORT': '22901',
}

with get_ssh_client('oracle-osa') as ssh:
    def run(command):
        _, output, error = ssh.exec_command(command, timeout=180)
        out = output.read().decode('utf-8')
        err = error.read().decode('utf-8')
        code = output.channel.recv_exit_status()
        if out:
            print(out, end='')
        if code:
            raise RuntimeError(f'Remote command exited {code}: {err}')

    run('set -e\nif ! id -u arkgram-review >/dev/null 2>&1; then '
        'useradd --system --home-dir /var/lib/arkgram-review --shell /usr/sbin/nologin arkgram-review; fi\n'
        'install -d -m 755 /opt/arkgram-review /opt/arkgram-review/wheels\n'
        'install -d -m 700 -o arkgram-review -g arkgram-review /var/lib/arkgram-review')
    with ssh.open_sftp() as sftp:
        for name in ['server.py', 'admin.html', 'review.html', 'requirements.txt']:
            (source_root / name).read_bytes().decode('utf-8')
            sftp.put(str(source_root / name), '/opt/arkgram-review/' + name)
            sftp.chmod('/opt/arkgram-review/' + name, 0o644)
        for wheel in args.wheel_dir.glob('*.whl'):
            sftp.put(str(wheel), '/opt/arkgram-review/wheels/' + wheel.name)
            sftp.chmod('/opt/arkgram-review/wheels/' + wheel.name, 0o644)
        with sftp.open('/etc/arkgram-review.env', 'w') as target:
            target.write(''.join(f'{key}={value}\n' for key, value in environment.items()))
        sftp.chmod('/etc/arkgram-review.env', 0o600)
        sftp.put(str(source_root / 'arkgram-review.service'), '/etc/systemd/system/arkgram-review.service')
        sftp.chmod('/etc/systemd/system/arkgram-review.service', 0o644)

    run('set -e\npython3 -m venv /opt/arkgram-review/venv\n'
        '/opt/arkgram-review/venv/bin/python -m pip install '
        '--find-links=/opt/arkgram-review/wheels --only-binary=:all: '
        '-r /opt/arkgram-review/requirements.txt\n'
        'systemctl daemon-reload\nsystemctl enable arkgram-review.service\n'
        'systemctl restart arkgram-review.service')

    proxy_path = '/opt/1panel/www/sites/secapi.telecom.moe/proxy/arkgram-review.conf'
    with ssh.open_sftp() as sftp:
        try:
            sftp.stat(proxy_path)
        except FileNotFoundError:
            pass
        else:
            run('cp -- ' + shlex.quote(proxy_path) + ' ' + shlex.quote(proxy_path + '.previous'))
        sftp.put(str(source_root / 'proxy.conf'), proxy_path)
        sftp.chmod(proxy_path, 0o644)
    run('set -e\ndocker exec 1Panel-openresty-CGfH openresty -t\n'
        'docker exec 1Panel-openresty-CGfH openresty -s reload\n'
        'systemctl is-active arkgram-review.service')

print('Service deployed. Account sign-in is still required.')
print('Owner setup: ' + credentials['base_url'] + 'admin/')
print('Private credentials: ' + str(credentials_path))
