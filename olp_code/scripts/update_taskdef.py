#!/usr/bin/env python3
import json, sys

svc = sys.argv[1]
repo = sys.argv[2]
tag = sys.argv[3]

# buildspec saves as /tmp/{short}-raw.json
# e.g. auth-service -> /tmp/auth-raw.json
short = svc.replace('-service', '')
input_file = f'/tmp/{short}-raw.json'
output_file = f'/tmp/taskdef-{svc}.json'

with open(input_file) as f:
    td = json.load(f)

for k in ['taskDefinitionArn','revision','status','requiresAttributes',
          'compatibilities','registeredAt','registeredBy']:
    td.pop(k, None)

for c in td.get('containerDefinitions', []):
    if c['name'] == svc:
        c['image'] = f"{repo}/olp/{svc}:{tag}"

with open(output_file, 'w') as f:
    json.dump(td, f, default=str)

print(f"taskdef-{svc}.json ready")