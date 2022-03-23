#! /bin/bash
# Adapted from optimus/package/scripts/release.sh
set -e
DIR="public-druid"
#if facing child pom not found then delete public-druid folder and rebuild
# look for  dir
if [ -d "$DIR" ]
then
        if [ "$(ls -A $DIR)" ]; then
          echo "$DIR is not Empty"
         # git fetch origin
        #  git checkout druid_main_test
         # git submodule update --rebase --remote
         #git submodule update --remote --merge
         #git submodule update --init --recursive --remote

        else
          git clone --recurse-submodules https://github.com/pinterest/druid.git public-druid
          echo "Cloned submodules"
        fi
else
        git clone --recurse-submodules https://github.com/pinterest/druid.git public-druid
        echo "Directory $DIR not found."
fi
pwd
git submodule status
#cd "../Druid-private-test"
ls
echo $(hostname)
# At some point this won't work locally and we'll have to install ecr-credential-helper, which already exists on devapp.
if [[ $(hostname) != "dev-"* ]]; then
    $(aws ecr get-login --no-include-email --region us-east-1)
    echo "this if "
fi

echo "dsdsd-- $hostname"
additional_mvn_args=""
if [ "$1" = "--distribution-only" ]; then
    additional_mvn_args="$additional_mvn_args -pl distribution"
fi

#mvn clean install -DreducedBuild -Pbundle-contrib-pinterest,dist -Ddocker.build -Dforbiddenapis.skip -Dcheckstyle.skip -DskipTests ${additional_mvn_args}

IMAGE_ID=$(docker images 998131032990.dkr.ecr.us-east-1.amazonaws.com/druid:latest --format="{{.ID}}")

IMAGE_SHORT=${IMAGE_ID:0:7}
echo ${IMAGE_ID}

docker tag druid:latest pinregistry.pinadmin.com/druid:${IMAGE_ID}

docker push pinregistry.pinadmin.com/druid:${IMAGE_ID}

# grab dev token
if [[ -z "$TOKEN" ]]; then
  TOKEN=$(KNOX_MACHINE_AUTH=$(hostname) knox get teletraan:optimus_dev_token || true)
  if [[ -z "$TOKEN" ]]; then
    # probably running on local machine, so try using knox on devapp
    TOKEN=$(ssh devapp "KNOX_MACHINE_AUTH=$(hostname) knox get teletraan:optimus_dev_token")
  fi
fi

if [[ -z "$TOKEN" ]]; then
  echo "Error: No token found! Can't push local release!"
  exit 1
fi

# Add registry explicitly to all teletraan files
cp -r ./teletraan /tmp/
if [[ $OSTYPE == "darwin"* ]]; then
    find /tmp/teletraan/ -name "serviceset*" -type f -exec sed -i '' 's/image: druid/image: druid\
        registry: pinregistry.pinadmin.com/g' {} +
else
    find /tmp/teletraan/ -name "serviceset*" -type f -exec sed -i 's/image: druid/image: druid\
        registry: pinregistry.pinadmin.com/g' {} +
fi

tar czvf "druid-${IMAGE_SHORT}.tar.gz" -C /tmp teletraan/

aws s3 cp --no-progress druid-${IMAGE_SHORT}.tar.gz s3://pinterest-alameda/druid/druid-${IMAGE_SHORT}.tar.gz

echo -n "${IMAGE_SHORT}" > tmp.latest

echo "${IMAGE_SHORT}"

aws s3 cp --no-progress tmp.latest s3://pinterest-alameda/druid/druid.latest

rm tmp.latest

curl -s -k --retry 2 -X POST https://teletraan.pinadmin.com/v1/builds -H "Authorization: token ${TOKEN}" -H "Content-Type: application/json" -d @- << DATA
    {
      "name": "druid",
      "repo": "DRUID",
      "branch": "private",
      "commit": "${IMAGE_ID}",
      "artifactUrl": "https://devrepo.pinadmin.com/druid/druid-${IMAGE_SHORT}.tar.gz",
      "commitDate": "$(($(date +%s) * 1000))",
      "publishInfo": "$(whoami)@$(hostname)"
    }
DATA
