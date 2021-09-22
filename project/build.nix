with import <nixpkgs> { };
let
  initScript = writeScript "binny-build-init" ''
     export LD_LIBRARY_PATH=
     sbt "$@"
  '';
in
buildFHSUserEnv {
  name = "binny-sbt";
  targetPkgs = pkgs: with pkgs; [
    netcat jdk8 wget which sbt jekyll
  ];
  runScript = initScript;
}
