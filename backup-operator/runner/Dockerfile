# borgmatic runner for the generated backup CronJobs. The upstream
# ghcr.io/borgmatic-collective/borgmatic image only works as root (/usr is 0744),
# which restricted Pod Security rejects.
FROM alpine:3.23

# postgresql18-client: pg_dump must be >= the server's major version (15 and 18 in use).
RUN apk add --no-cache \
        borgmatic \
        borgbackup \
        openssh-client-default \
        postgresql18-client \
        nss_wrapper

COPY --chmod=0755 entrypoint.sh /usr/local/bin/entrypoint.sh

# Distinct warning codes, so the generated borg_exit_codes can fail on
# unreadable files without failing on files that changed mid-backup.
ENV BORG_EXIT_CODES=modern

USER 10001:10001
ENTRYPOINT ["/usr/local/bin/entrypoint.sh"]
CMD ["borgmatic", "--help"]
