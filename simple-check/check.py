import time
import random

print("Starting a simple check...")
time.sleep(2)  # Simulate doing some work

# Simulate a check that can sometimes fail
if random.random() > 0.2:  # 80% chance of success
    print("Check finished successfully!")
    # In Kubernetes, an exit code of 0 means success
    exit(0)
else:
    print("Check FAILED.")
    # A non-zero exit code tells Kubernetes the Job failed
    exit(1)
