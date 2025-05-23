# LLM News Crawler

A distributed web crawler system with LLM integration for intelligent news content processing.

## Architecture Overview

The system consists of several microservices:

1. **URL Frontier Service**: Manages the URL queue and distributes URLs to crawlers
2. **Crawler Service**: Fetches web pages and extracts content
3. **Content Processor Service**: Processes content using LLM for analysis
4. **URL Filter Service**: Filters and prioritizes URLs
5. **Common Module**: Shared code and models

## Prerequisites

- Java 17 or higher
- Maven 3.8+
- MongoDB 4.4+
- Apache Kafka 3.x
- Redis 6.x
- Elasticsearch 8.x

## Setup Instructions

1. Start the required services:

2. Build the project:

## Configuration

Each service has its own `application.yml` file with configuration for:
- Server ports
- MongoDB connection
- Kafka settings
- Redis configuration
- Elasticsearch settings


## Development

1. Clone the repository
2. Import the project into your IDE
3. Make sure to have all required services running
4. Run the application in development mode

## Contributing

1. Fork the repository
2. Create a feature branch
3. Commit your changes
4. Push to the branch
5. Create a Pull Request
