cmake_minimum_required(VERSION 3.4.1)

set(CMAKE_C_FLAGS_RELEASE "${CMAKE_C_FLAGS_RELEASE} -s")
set(CMAKE_CXX_FLAGS_RELEASE "${CMAKE_CXX_FLAGS_RELEASE} -s")

include_directories("src/main/cpp")

find_library( log-lib
              log )

add_library( # Sets the name of the library.
             native-utils

             # Sets the library as a shared library.
             SHARED

             src/main/cpp/p.cpp
             )

target_link_libraries( # Specifies the target library.
                       native-utils

                       ${log-lib} )