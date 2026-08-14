#include <iconv.h>
int main() {
    iconv_t t = iconv_open("UTF-8", "UTF-8");
    if (t == (iconv_t)-1) return 1;
    iconv_close(t);
    return 0;
}
