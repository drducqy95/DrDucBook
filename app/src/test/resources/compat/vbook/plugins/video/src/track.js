function execute() {
  return Response.success({
    data: "https://media.example.test/video/master.m3u8",
    headers: {Referer: "https://video.example.test/"},
    subtitles: [{label: "Vietnamese", url: "https://media.example.test/video/vi.vtt"}]
  });
}
