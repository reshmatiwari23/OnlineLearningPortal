#!/usr/bin/env python3
import sys, subprocess, os

svc = sys.argv[1]
app_name = sys.argv[2]
region = sys.argv[3]
cluster = os.environ.get('CLUSTER', 'olp-cluster')

# buildspec saves as /tmp/{short}-arn.txt
# e.g. auth-service -> /tmp/auth-arn.txt
short = svc.replace('-service', '')

with open(f'/tmp/appspec-{svc}.yml') as f:
    appspec_content = f.read()

with open(f'/tmp/{short}-arn.txt') as f:
    task_def_arn = f.read().strip()

try:
    result = subprocess.run([
        'aws', 'deploy', 'create-deployment',
        '--application-name', app_name,
        '--deployment-group-name', f'{svc}-deploy-group',
        '--revision', f"revisionType=AppSpecContent,appSpecContent={{content='{appspec_content}'}}",
        '--deployment-config-name', 'CodeDeployDefault.ECSAllAtOnce',
        '--description', f'Blue/Green {svc}',
        '--region', region,
        '--query', 'deploymentId',
        '--output', 'text'
    ], capture_output=True, text=True, timeout=30)

    if result.returncode == 0 and result.stdout.strip():
        print(f"✅ {svc} Blue/Green → {result.stdout.strip()}")
    else:
        raise Exception(result.stderr)

except Exception as e:
    print(f"⚠️  {svc} CodeDeploy failed ({e}) — using rolling fallback")
    subprocess.run([
        'aws', 'ecs', 'update-service',
        '--cluster', cluster,
        '--service', svc,
        '--task-definition', task_def_arn,
        '--force-new-deployment',
        '--region', region,
        '--query', 'service.serviceName',
        '--output', 'text'
    ])