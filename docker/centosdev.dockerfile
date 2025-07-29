ARG BASE_IMAGE=quay.io/centos/centos:stream9
FROM ${BASE_IMAGE} AS presto-javadev

ENV PYTHON_VERSION=3.11.9 \
    CC=/opt/rh/gcc-toolset-12/root/bin/gcc \
    CXX=/opt/rh/gcc-toolset-12/root/bin/g++ \
    JAVA_HOME=/usr/lib/jvm/jre-11-openjdk

RUN --mount=type=cache,target=/var/cache/dnf,sharing=locked \
    --mount=type=cache,target=/usr/local/src,sharing=locked \
    dnf update -y && dnf install -y java-11-openjdk-headless less procps wget diffutils git vim \
        make gcc-toolset-12 binutils zlib-devel bzip2-devel xz-devel openssl-devel \
        libffi-devel readline-devel sqlite-devel tar which epel-release && \
    dnf update -y && dnf install -y ccache && \
    cd /usr/local/src && \
    wget -c https://www.python.org/ftp/python/${PYTHON_VERSION}/Python-${PYTHON_VERSION}.tgz && \
    tar xvf Python-${PYTHON_VERSION}.tgz && \
    cd Python-${PYTHON_VERSION} && \
    ./configure --enable-optimizations && \
    make -j$(nproc) && \
    make altinstall && \
    alternatives --install /usr/bin/python python /usr/local/bin/python3.11 1 && \
    alternatives --install /usr/bin/python3 python3 /usr/local/bin/python3.11 1

RUN --mount=type=cache,target=/usr/local/src/.m2 \
    --mount=type=bind,source=..,target=/usr/local/src/presto,readonly=false \
    find /usr/local/src/.m2 || true && \
    cd /usr/local/src/presto && \
    MAVEN_USER_HOME=/usr/local/src/.m2 ./mvnw clean install -Dmaven.repo.local=/usr/local/src/.m2/repository -DskipTests && \
    for dir in presto-*; do \
      rm -rf /usr/local/src/.m2/repository/com/facebook/presto/$dir; \
    done && \
    cp -a /usr/local/src/.m2 /root/

FROM ${BASE_IMAGE} AS presto-nativedev
ARG EXTRA_CMAKE_FLAGS
ARG OSNAME=centos
ARG BUILD_TYPE=Release
ARG BUILD_DIR='release'
ARG EXTRA_CMAKE_FLAGS=''
ARG NUM_THREADS=2
ENV INSTALL_PREFIX=/usr/local \
    DEPENDENCY_DIR=/usr/local/src/deps-download \
    LANG=C.UTF-8 \
    LC_ALL=C.UTF-8 \
    CC=/opt/rh/gcc-toolset-12/root/bin/gcc \
    CXX=/opt/rh/gcc-toolset-12/root/bin/g++ \
    PROMPT_ALWAYS_RESPOND=n \
    PATH=/usr/local/cuda/bin:${PATH} \
    NVIDIA_VISIBLE_DEVICES=all \
    NVIDIA_DRIVER_CAPABILITIES="compute,utility" \
    BUILD_ROOT=/usr/local/src/presto/presto-native-execution \
    EXTRA_CMAKE_FLAGS=${EXTRA_CMAKE_FLAGS} \
    VELOX_ARROW_CMAKE_PATCH=/usr/local/src/presto/presto-native-execution/velox/CMake/resolve_dependency_modules/arrow/cmake-compatibility.patch


RUN --mount=type=cache,target=/var/cache/dnf,sharing=locked \
    dnf update -y && dnf install -y less procps wget diffutils git vim \
        make gcc-toolset-12 binutils zlib-devel bzip2-devel xz-devel openssl-devel \
        libffi-devel readline-devel sqlite-devel tar which epel-release && \
        dnf update -y && dnf install -y ccache


RUN --mount=type=bind,source=..,target=/usr/local/src/presto,readonly=false \
    cd ${BUILD_ROOT} && \
    make submodules && \
    git submodule

RUN --mount=type=bind,source=../presto-native-execution,target=/usr/local/src/presto/presto-native-execution,readonly=false \
    ls -l /usr/local/src/deps || true && \
    ls -l /usr/local/include || true && \
    ls -l /usr/local/src/deps-download || true && \
    rm -rf /usr/local/src/deps-download && \
    find /usr/local -name "fmt*config.cmake" || true && \
    cd ${BUILD_ROOT} && \
    make submodules && \
    git submodule && \
    env && \
    ls -l /usr/local/src/presto/presto-native-execution/velox/CMake/resolve_dependency_modules/arrow/ && \
    export INSTALL_PREFIX=/usr/local && \
    export DEPENDENCY_DIR=/usr/local/src/deps && \
    rm -rf /usr/local/src/deps/arrow && \
    ./scripts/setup-centos.sh && \
    ./velox/scripts/setup-centos9.sh install_adapters && \
    ./scripts/setup-adapters.sh && \
    source ../velox/scripts/setup-centos9.sh && \
    install_clang15 && \
    cp -a /usr/local/src/deps /usr/local/src/deps-download

RUN --mount=type=bind,source=..,target=/usr/local/src/presto,readonly=false \
    cd ${BUILD_ROOT} && \
    ccache -sz -v && \
    make --directory="${BUILD_ROOT}" cmake-and-build BUILD_TYPE=${BUILD_TYPE} BUILD_DIR=${BUILD_DIR} BUILD_BASE_DIR=${BUILD_BASE_DIR} && \
    ccache -sz -v && \
    mkdir -p /opt/prestissimo/bin && \
    mkdir -p /opt/prestissimo/lib && \
    cp ${BUILD_ROOT}/${BUILD_BASE_DIR}/${BUILD_DIR}/presto_cpp/main/presto_server /opt/prestissimo/bin/ && \
    !(LD_LIBRARY_PATH=${LD_LIBRARY_PATH}:/usr/local/lib:/usr/local/lib64 ldd ${BUILD_ROOT}/${BUILD_BASE_DIR}/${BUILD_DIR}/presto_cpp/main/presto_server  | grep "not found") && \
    LD_LIBRARY_PATH=${LD_LIBRARY_PATH}:/usr/local/lib:/usr/local/lib64 ldd ${BUILD_ROOT}/${BUILD_BASE_DIR}/${BUILD_DIR}/presto_cpp/main/presto_server | awk 'NF == 4 { system("cp " $3 " /opt/prestissimo/lib/") }' && \
    echo "/opt/prestissimo/lib" > /etc/ld.so.conf.d/prestissimo.conf && ldconfig && \
    cp -a ./etc /opt/presto-server/etc && \
    chmod -R 0755 /opt/presto-server/bin && \
    chmod -R 0755 /opt/presto-server/lib && \
    chmod -R 0755 /opt/presto-server/etc && \
    echo "#!/bin/sh\nGLOG_logtostderr=1 /opt/prestissimo/bin/presto_server --etc-dir=/opt/prestissimo/etc" /opt/prestissimo.sh && \
    chmod +x /opt/prestissimo.sh
