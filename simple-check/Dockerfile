# Start from a lightweight Python base image
FROM python:3.9-slim

# Set the working directory
WORKDIR /app

# Copy our check script into the container
COPY check.py .

# The command to run when the container starts
CMD ["python", "check.py"]
