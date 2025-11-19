# Build the image
# docker build -t practice-ansible-config-runner:1.0.0 .

# Push the image
# docker push practice-ansible-config-runner:1.0.0

# Start from a minimal base image with Ansible pre-installed
FROM cytopia/ansible:2.10

# Copy the playbook and template into the image
COPY ./playbooks/generate_config.yml /ansible/playbooks/
COPY ./templates/config.json.j2 /ansible/templates/

WORKDIR /ansible
