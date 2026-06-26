#!/usr/bin/env python3
import json, sys

svc = sys.argv[1]
repo = sys.argv[2]
tag = sys.argv[3]

with open(f'/tmp/{svc}-raw.json') as f:
    td = json.load(f)

# Remove read-only fields
for k in ['taskDefinitionArn','revision','status','requiresAttributes',
          'compatibilities','registeredAt','registeredBy']:
    td.pop(k, None)

# Update image
for c in td.get('containerDefinitions', []):
    if c['name'] == svc:
        c['image'] = f"{repo}/olp/{svc}:{tag}"

with open(f'/tmp/taskdef-{svc}.json', 'w') as f:
    json.dump(td, f, default=str)

print(f"taskdef-{svc}.json ready")
