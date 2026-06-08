# Patch OpenSSL 15-android.conf to work on Windows with NDK 21+
# The problem: OpenSSL's Perl detection logic uses Unix-style path matching
# (which("clang") =~ m|^$ndk/.*/prebuilt/...|) which fails on Windows because
# paths use different separators and formats. This causes it to fall through
# to the gcc branch, but NDK 21+ doesn't have gcc.
#
# The fix: Replace the entire compiler detection block with a simple assignment
# that sets CC to clang and CROSS_COMPILE to empty string, which is exactly
# what the successful NDK 19+ detection path would do.

file(READ "Configurations/15-android.conf" CONF_FILE)

# Normalize line endings to LF for reliable matching
string(REPLACE "\r\n" "\n" CONF_FILE "${CONF_FILE}")

# Replace the entire compiler detection if/elsif/else chain.
# We replace from the "see if there is NDK clang" comment through the closing
# brace of the else block, with simple direct assignments that match what
# the NDK 19+ (unified toolchain) path would produce.
string(REPLACE
[==[            # see if there is NDK clang on $PATH, "universal" or "standalone"
            if (which("clang") =~ m|^$ndk/.*/prebuilt/([^/]+)/|) {
                my $host=$1;
                # harmonize with gcc default
                my $arm = $ndkver > 16 ? "armv7a" : "armv5te";
                (my $tridefault = $triarch) =~ s/^arm-/$arm-/;
                (my $tritools   = $triarch) =~ s/(?:x|i6)86(_64)?-.*/x86$1/;
                if (length $sysroot) {
                    $cflags .= " -target $tridefault "
                            .  "-gcc-toolchain \$($ndk_var)/toolchains"
                            .  "/$tritools-4.9/prebuilt/$host";
                    $user{CC} = "clang" if ($user{CC} !~ m|clang|);
                } else {
                    $user{CC} = "$tridefault$api-clang";
                }
                $user{CROSS_COMPILE} = undef;
                if (which("llvm-ar") =~ m|^$ndk/.*/prebuilt/([^/]+)/|) {
                    $user{AR} = "llvm-ar";
                    $user{ARFLAGS} = [ "rs" ];
                    $user{RANLIB} = ":";
                }
            } elsif ($is_standalone_toolchain) {
                my $cc = $user{CC} // "clang";
                # One can probably argue that both clang and gcc should be
                # probed, but support for "standalone toolchain" was added
                # *after* announcement that gcc is being phased out, so
                # favouring clang is considered adequate. Those who insist
                # have option to enforce test for gcc with CC=gcc.
                if (which("$triarch-$cc") !~ m|^$ndk|) {
                    die "no NDK $triarch-$cc on \$PATH";
                }
                $user{CC} = $cc;
                $user{CROSS_COMPILE} = "$triarch-";
            } elsif ($user{CC} eq "clang") {
                die "no NDK clang on \$PATH";
            } else {
                if (which("$triarch-gcc") !~ m|^$ndk/.*/prebuilt/([^/]+)/|) {
                    die "no NDK $triarch-gcc on \$PATH";
                }
                $cflags .= " -mandroid";
                $user{CROSS_COMPILE} = "$triarch-";
            }]==]
[==[            # Patched for Windows NDK 21+ cross-compilation:
            # Skip all path-based compiler detection and use the unified
            # LLVM toolchain directly. CC/AR/RANLIB are set via environment.
            # NDK 21+ clang has built-in sysroot, so clear $sysroot to
            # prevent broken path concatenation on Windows.
            {
                my $arm = $ndkver > 16 ? "armv7a" : "armv5te";
                (my $tridefault = $triarch) =~ s/^arm-/$arm-/;
                $user{CC} = "$tridefault$api-clang";
                $user{CROSS_COMPILE} = "";
                $sysroot = "";
            }]==]
CONF_FILE "${CONF_FILE}")

file(WRITE "Configurations/15-android.conf" "${CONF_FILE}")

# Patch unix-checker.pm to not die on Windows
file(READ "Configurations/unix-checker.pm" CHECKER_FILE)
string(REPLACE "die" "print" CHECKER_FILE "${CHECKER_FILE}")
file(WRITE "Configurations/unix-checker.pm" "${CHECKER_FILE}")
