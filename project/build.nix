with import <nixpkgs> { };
let
  initScript = writeScript "binny-build-init" ''
     export LD_LIBRARY_PATH=
     sbt -java-home ${jdk11}/lib/openjdk "$@"
  '';
in
buildFHSUserEnv {
  name = "binny-sbt";
  targetPkgs = pkgs: with pkgs; [
    netcat jdk11 wget which sbt jekyll
  ];
  runScript = initScript;
}
