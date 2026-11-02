"""One process: RAM freshness/progress and instance lock must not be forked."""
bind = "0.0.0.0:18765"
workers = 1
worker_class = "gthread"
threads = 4
preload_app = False
timeout = 15
graceful_timeout = 10
keepalive = 2
limit_request_line = 1024
limit_request_fields = 16
limit_request_field_size = 1024
accesslog = None
# Parser errors may contain untrusted URI/header text. Do not emit them.
errorlog = "/dev/null"
capture_output = False
umask = 0o077
control_socket_disable = True
