// Fathom - simple website analytics - https://github.com/usefathom/fathom
(function(f, a, t, h, o, m){
  a[h]=a[h]||function(){
    (a[h].q=a[h].q||[]).push(arguments)
  };
  o=f.createElement('script'),
  m=f.getElementsByTagName('script')[0];
  o.async=1; o.src=t; o.id='fathom-script';
  m.parentNode.insertBefore(o,m)
})(document, window, '//webstats.daheim.site/tracker.js', 'fathom');
fathom('set', 'siteId', 'MMYRN');
fathom('trackPageview');
