FROM crpi-ekwujpeg6f954ar3.cn-wulanchabu.personal.cr.aliyuncs.com/cloud-ops-hub/jenkins:lts-jdk21-amd64

USER root

ARG DOCKER_CLI_VERSION=27.5.1
ARG KUBECTL_VERSION=v1.32.3

RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates curl git gzip openssh-client tar \
    && rm -rf /var/lib/apt/lists/*

RUN curl -fsSL "https://download.docker.com/linux/static/stable/x86_64/docker-${DOCKER_CLI_VERSION}.tgz" -o /tmp/docker.tgz \
    && tar -xzf /tmp/docker.tgz -C /tmp \
    && install -m 0755 /tmp/docker/docker /usr/local/bin/docker \
    && rm -rf /tmp/docker /tmp/docker.tgz

RUN set -eux; \
    kubectl_urls="\
https://dl.k8s.io/release/${KUBECTL_VERSION}/bin/linux/amd64/kubectl \
https://storage.googleapis.com/kubernetes-release/release/${KUBECTL_VERSION}/bin/linux/amd64/kubectl"; \
    for url in ${kubectl_urls}; do \
      if curl --retry 5 --retry-delay 3 --retry-connrefused --connect-timeout 20 -fL "${url}" -o /usr/local/bin/kubectl; then \
        chmod 0755 /usr/local/bin/kubectl; \
        exit 0; \
      fi; \
      echo "Failed to download kubectl from ${url}, trying next mirror..." >&2; \
    done; \
    echo "Unable to download kubectl ${KUBECTL_VERSION} from all configured mirrors." >&2; \
    exit 1

USER jenkins
