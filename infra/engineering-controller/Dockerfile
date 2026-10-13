FROM python:3.12-slim
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY controller.py .
RUN useradd -u 10001 -r agent
USER agent
CMD ["uvicorn", "controller:app", "--host", "0.0.0.0", "--port", "8080"]
