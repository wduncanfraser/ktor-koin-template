# Idempotent OpenFGA provisioner.
FROM alpine:3.24
RUN apk add --no-cache jq
COPY --from=openfga/cli:latest /fga /usr/local/bin/fga
COPY provision.sh /provision.sh
ENTRYPOINT ["/bin/sh", "/provision.sh"]
