#!/usr/bin/env bash

# program : install-local.sh
#  author : luanjia@taobao.com
#    date : 2017-01-01
# version : 0.0.0.1

# exit shell with err_code
# $1 : err_code
# $2 : err_msg
exit_on_err()
{
    [[ ! -z "${2}" ]] && echo "${2}" 1>&2
    exit ${1}
}


# the sandbox main function
function main() {

    # check permission
    # [[ ! -w ${SANDBOX_INSTALL_PREFIX} ]] \
    #    && exit_on_err 1 "permission denied, ${SANDBOX_INSTALL_PREFIX} is not writable."


    local SANDBOX_INSTALL_LOCAL="/data/appdatas/sandbox"

    # clean if existed
    [ -w ${SANDBOX_INSTALL_LOCAL} ] \
        && rm -rf ${SANDBOX_INSTALL_LOCAL}

    # create install dir
    mkdir -p ${SANDBOX_INSTALL_LOCAL} \
        || exit_on_err 1 "permission denied, create ${SANDBOX_INSTALL_LOCAL} failed."

    # copy file
    cp -r ./cfg ${SANDBOX_INSTALL_LOCAL}/ \
        && cp -r ./lib ${SANDBOX_INSTALL_LOCAL}/ \
        && cp -r ./module ${SANDBOX_INSTALL_LOCAL}/ \
        && cp -r ./provider ${SANDBOX_INSTALL_LOCAL}/ \
        && cp -r ./user-module ${SANDBOX_INSTALL_LOCAL}/ \
        && mkdir -p ${SANDBOX_INSTALL_LOCAL}/bin \
        || exit_on_err 1 "permission denied, copy file failed."

    # replace sandbox.sh\`s ${SANDBOX_HOME_DIR}
    cat ./bin/sandbox.sh \
        | sed "s:typeset SANDBOX_HOME_DIR:typeset SANDBOX_HOME_DIR=${SANDBOX_INSTALL_LOCAL}:g" \
        > ${SANDBOX_INSTALL_LOCAL}/bin/sandbox.sh \
        && chmod +x ${SANDBOX_INSTALL_LOCAL}/bin/sandbox.sh \
        || exit_on_err 1 "permission denied, replace ${SANDBOX_INSTALL_LOCAL}/bin/sandbox.sh failed."

    # got sandbox's version
    local SANDBOX_VERSION=$(cat ${SANDBOX_INSTALL_LOCAL}/cfg/version)

    echo "VERSION=${SANDBOX_VERSION}"
    echo "PATH=${SANDBOX_INSTALL_LOCAL}"
    echo "install sandbox successful."

}

main "${@}"
