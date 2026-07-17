import logging.config
import logging

LOGGING_CONFIG = {
    "version": 1,
    "disable_existing_loggers": False,  # Keeps module-level loggers active
    "formatters": {
        "standard": {
            "format": "%(asctime)s [%(levelname)s] %(name)s: %(message)s"
        },
        "colored": {
            "()": "colorlog.ColoredFormatter",
            "format": "%(log_color)s%(asctime)s [%(levelname)s]%(reset)s %(name)s: %(blue)s%(message)s",
            'datefmt': '%Y-%m-%d %H:%M:%S',
            "log_colors": {
                "DEBUG": "cyan",
                "INFO": "green",
                "WARNING": "yellow",
                "ERROR": "red",
                "CRITICAL": "bold_red",
            },
        },
    },
    "handlers": {
        "console": {
            "class": "logging.StreamHandler",
            "level": "DEBUG",
            "formatter": "colored",
            "stream": "ext://sys.stdout",
        },
    },
    "loggers": {
        # The root logger catches everything by default
        "": {
            "handlers": ["console"],
            "level": "INFO",
        },
    },
}

SETUP = False

def get_logger(name: str):
    global SETUP
    if not SETUP:
        logging.config.dictConfig(LOGGING_CONFIG)
        SETUP = True

    return logging.getLogger(name)