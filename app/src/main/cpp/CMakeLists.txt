cmake_minimum_required(VERSION 3.22.1)
project("ghostlock")

add_library(ghostlock SHARED
    ghostlock-bridge.c
    # 未来在这里加入 ghostlock exploit 的源文件
    # trigger.c
    # spray.c
    # rb_write.c
    # root.c
    # ...
)

target_link_libraries(ghostlock
    log
)

target_compile_options(ghostlock PRIVATE
    -Wall -Wextra
    -O2
    -fvisibility=hidden
    -fno-stack-protector
)

# 当 ghostlock_main 在单独文件中时启用
# set_target_properties(ghostlock PROPERTIES LINKER_LANGUAGE C)
