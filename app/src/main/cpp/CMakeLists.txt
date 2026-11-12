cmake_minimum_required(VERSION 3.22.1)
project("danzinject" LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(danzinject SHARED native-lib.cpp)

target_compile_options(danzinject PRIVATE -Wall -Wextra -O3 -ffast-math -fvisibility=hidden)

find_library(log-lib log)
target_link_libraries(danzinject ${log-lib})
