#!/usr/bin/env python3
import sys

svc = sys.argv[1]
port = sys.argv[2]
task_def_arn = sys.argv[3].strip()

content = f"""version: 0.0
Resources:
  - TargetService:
      Type: AWS::ECS::Service
      Properties:
        TaskDefinition: "{task_def_arn}"
        LoadBalancerInfo:
          ContainerName: "{svc}"
          ContainerPort: {port}
        PlatformVersion: "1.4.0"
"""

with open(f'/tmp/appspec-{svc}.yml', 'w') as f:
    f.write(content)

print(f"appspec-{svc}.yml created")
