#include <jni.h>
#include <stdint.h>
#include <stdlib.h>
#include <stdio.h>
#include <errno.h>
#include <time.h>
#include <unistd.h>
#include <signal.h>
#include <sys/select.h>
#include <sys/time.h>
#include "zdtun.h"

typedef struct { JNIEnv *env; jobject service; jmethodID protect; int fd; FILE *pcap; int failed; unsigned count; } capture_ctx;
static int dump_packet(capture_ctx *ctx, const void *buf, uint32_t len) {
    struct timeval tv; gettimeofday(&tv, NULL);
    uint32_t h[4]={(uint32_t)tv.tv_sec,(uint32_t)tv.tv_usec,len,len};
    if (fwrite(h,sizeof(h),1,ctx->pcap)!=1 || fwrite(buf,len,1,ctx->pcap)!=1) {ctx->failed=1;return -1;}
    if (++ctx->count % 128 == 0 && fflush(ctx->pcap) != 0) {ctx->failed=1;return -1;} return 0;
}
static int send_client(zdtun_t *tun,zdtun_pkt_t *pkt,const zdtun_conn_t *conn) {
    capture_ctx *ctx=zdtun_userdata(tun); (void)conn;
    if (dump_packet(ctx,pkt->buf,pkt->len)) return -1;
    ssize_t n=write(ctx->fd,pkt->buf,pkt->len);
    if(n != pkt->len) {ctx->failed=1;return -1;} return 0;
}
static void protect_socket(zdtun_t *tun,socket_t sock) {
    capture_ctx *ctx=zdtun_userdata(tun);
    jboolean ok=(*ctx->env)->CallBooleanMethod(ctx->env,ctx->service,ctx->protect,(jint)sock);
    if ((*ctx->env)->ExceptionCheck(ctx->env) || !ok) {ctx->failed=1;close(sock);}
}
JNIEXPORT jint JNICALL Java_dev_rakrecorder_RecorderVpnService_runTunnel(JNIEnv *env,jobject service,jint fd,jstring path) {
    const char *name=(*env)->GetStringUTFChars(env,path,NULL); if(!name)return -1;
    FILE *file=fopen(name,"wb");(*env)->ReleaseStringUTFChars(env,path,name);if(!file)return -2;
    uint32_t magic=0xa1b2c3d4, zero=0, snap=65535, link=101;uint16_t major=2,minor=4;
    if(fwrite(&magic,4,1,file)!=1 || fwrite(&major,2,1,file)!=1 || fwrite(&minor,2,1,file)!=1 || fwrite(&zero,4,1,file)!=1 || fwrite(&zero,4,1,file)!=1 || fwrite(&snap,4,1,file)!=1 || fwrite(&link,4,1,file)!=1) {fclose(file);return -3;}
    jclass cls=(*env)->GetObjectClass(env,service);jmethodID protect=(*env)->GetMethodID(env,cls,"protect","(I)Z"),stopped=(*env)->GetMethodID(env,cls,"isStopping","()Z");
    if(!protect || !stopped) {fclose(file);return -4;}
    capture_ctx ctx={.env=env,.service=service,.protect=protect,.fd=fd,.pcap=file};
    zdtun_callbacks_t callbacks={.send_client=send_client,.on_socket_open=protect_socket};
    zdtun_t *tun=zdtun_init(&callbacks,&ctx);if(!tun){fclose(file);return -5;}
    signal(SIGPIPE,SIG_IGN); uint8_t buf[65535]; time_t last=time(NULL); int result=0;
    while(!ctx.failed && !(*env)->CallBooleanMethod(env,service,stopped)) {
        fd_set rd,wr;int maxfd=0;zdtun_fds(tun,&maxfd,&rd,&wr);if(fd<0 || fd>=FD_SETSIZE){result=-6;break;}FD_SET(fd,&rd);if(fd>maxfd)maxfd=fd;
        struct timeval timeout={.tv_sec=1,.tv_usec=0};int rc=select(maxfd+1,&rd,&wr,NULL,&timeout);
        if(rc<0){if(errno==EINTR)continue;result=-7;break;}
        if(rc>0) {
            if(FD_ISSET(fd,&rd)) {ssize_t n=read(fd,buf,sizeof(buf));if(n<=0){result=-8;break;}if(dump_packet(&ctx,buf,(uint32_t)n))break;zdtun_easy_forward(tun,(char*)buf,(int)n);}
            zdtun_handle_fd(tun,&rd,&wr);
        }
        if(time(NULL)-last>=3){zdtun_purge_expired(tun);last=time(NULL);if(fflush(file)!=0){ctx.failed=1;break;}}
        if((*env)->ExceptionCheck(env)){result=-9;break;}
    }
    zdtun_finalize(tun);if(fflush(file)!=0)ctx.failed=1;if(fclose(file)!=0)ctx.failed=1;if(ctx.failed && !result)result=-10;return result;
}
