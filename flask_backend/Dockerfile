FROM python:3.12-slim

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY . .

EXPOSE 5454

# Not: gunicorn Linux/Docker içindir. Windows'ta doğrudan `python app.py` kullanın.
CMD ["gunicorn", "app:app", "--bind", "0.0.0.0:5454", "--workers", "2"]