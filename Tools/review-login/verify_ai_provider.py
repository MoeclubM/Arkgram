"""Verify the real reviewer provider in CI without publishing its API key."""
import json
import os
import urllib.request

config = json.loads(os.environ['REVIEW_AI_CONFIG'])
assert config['api_format'] == 'chat_completions'
body = json.dumps({
    'model': config['model'],
    'messages': [{'role': 'user', 'content': 'Translate into English. Return only the translation: 你好，世界。'}],
}).encode('utf-8')
request = urllib.request.Request(config['endpoint'], data=body, headers={
    'Authorization': 'Bearer ' + config['api_key'],
    'Content-Type': 'application/json',
})
with urllib.request.urlopen(request, timeout=90) as response:
    result = json.load(response)
translation = result['choices'][0]['message']['content'].strip().lower()
assert 'hello' in translation and 'world' in translation, 'Provider did not return the requested translation'
print('The configured reviewer AI model completed a real translation successfully.')
