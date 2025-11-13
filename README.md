# hello-spring-ai-rag

## 개요
- Spring Boot + Spring AI + Ollama + Milvus 기반 RAG 예제 프로젝트
- Docker Compose로 로컬 LLM/벡터DB 환경을 구성하고 애플리케이션을 실행합니다

## 요구사항
- `Java 21`
- `Docker` 및 `Docker Compose`
- `Gradle`(래퍼 포함): `./gradlew`

## Docker 서비스 구성
- `ollama` 서버: `http://localhost:11434`
- `milvus-standalone`: 포트 `19530`(DB), `9091`(헬스체크)
- `etcd`, `minio`는 Milvus 의존 서비스로 함께 기동
- `open-webui`: `http://localhost:3000` (옵션)
- 데이터 볼륨: `./data/ollama`, `./data/milvus`, `./data/minio`, `./data/etcd`, `./data/open-webui`

## 시작하기
- 컨테이너 기동: `docker compose up -d`
- Ollama 모델 설치(컨테이너 내부 실행):
  - `docker exec -it ollama ollama pull nomic-embed-text`
  - `docker exec -it ollama ollama pull gemma:2b-instruct`
- 확인:
  - 모델 목록: `docker exec -it ollama ollama list`
  - Ollama 버전: `curl http://localhost:11434/api/version`
  - Milvus 헬스체크: `curl http://localhost:9091/healthz`

## 애플리케이션 실행
- 기본 실행: `./gradlew bootRun --args=--server.port=8080`
- 주요 설정(`src/main/resources/application.yaml` 기본값):
  - `spring.ai.ollama.base-url`: `http://localhost:11434`
  - `spring.ai.ollama.embedding.options.model`: `nomic-embed-text`
  - `spring.ai.ollama.chat.options.model`: `gemma:2b-instruct`
- 환경변수로 변경 가능:
  - `export OLLAMA_BASE_URL=http://localhost:11434`
  - `export OLLAMA_EMBED_MODEL=mxbai-embed-large` (예시)
  - `export OLLAMA_CHAT_MODEL=llama3.2:latest` (예시)
  - Milvus 관련: `MILVUS_DATABASE`, `MILVUS_COLLECTION`, `MILVUS_INIT_SCHEMA`, `MILVUS_EMBED_DIM`, `MILVUS_INDEX_TYPE`, `MILVUS_METRIC_TYPE`

## 문제 해결
- HTTP 404: `model "..." not found` → 해당 모델을 먼저 풀하세요(`ollama pull ...`).
- MacOS에서 DNS 네이티브 경고 → 프로젝트에 Mac용 네이티브 리졸버가 포함되어 있어 자동 처리됩니다.
- 슬림 컨테이너 디버깅이 필요하면: `docker debug ollama`로 디버그 셸 진입 가능.

## 종료 및 정리
- 컨테이너 중지: `docker compose down`
- 데이터 유지: 볼륨/`./data`는 유지됩니다. 초기화가 필요하면 수동 삭제에 주의하세요.
