# LLM News Crawler

A distributed web crawler system with LLM integration for intelligent news content processing.

## Architecture Overview

The system consists of several microservices:

1. **Frontier Service**: Manages the URL queue and distributes URLs to crawlers.
2. **Fetcher Service**: Fetches web pages.
3. **LLM Parsing Service**: Uses LLM to analyze content, extract new URLs, filter them, and send valid ones back to the Frontier.
4. **Content Storing Service**: Stores processed content in Elasticsearch and provides APIs for keyword and semantic search.
5. **Monitoring**: Exports metrics to monitor the performance and health of each service.

## Technologies

- Java Spring Boot
- MongoDB
- Apache Kafka
- Redis
- Elasticsearch
- Prometheus
- Grafana
- Gemini (Gemini 2.0 Flash-Lite & text-embedding-004)

## Setup

1. Clone the repository
2. Import the project into your IDE
3. Ensure all required services (MongoDB, Kafka, etc.) are running.
4. Run the application in development mode
5. Alternatively, you can use Docker Compose to spin up all services:
```
docker compose up --build -d
```
> **Note:**  
> Ensure Docker and Docker Compose are installed on your system.
> Don't forget to configure your `GOOGLE_API_KEY` in a `.env` file for the abstractive summarization to work properly.

## Contributing

1. Fork the repository
2. Create a feature branch
3. Commit your changes
4. Push to the branch
5. Create a Pull Request
