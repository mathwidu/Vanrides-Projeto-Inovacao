FROM python:3.13-slim@sha256:2d44738ebf4ce5f63b9d9ea871e9c7cfdc0426f7b0c97cac915fd88ded1d86bb
WORKDIR /opt/gps-spike
COPY requirements-production.txt ./
RUN python -m pip install --no-cache-dir --require-hashes -r requirements-production.txt \
    && groupadd --gid 10001 spike \
    && useradd --uid 10001 --gid 10001 --no-create-home spike \
    && mkdir /data && chown spike:spike /data && chmod 700 /data
COPY receiver ./receiver
COPY gunicorn.conf.py ./
USER 10001:10001
EXPOSE 18765
CMD ["gunicorn", "--config", "gunicorn.conf.py", "receiver.web:create_app()"]
