function execute(query, page) {
  return Response.success([
    {
      name: "Compatibility Video",
      author: "Video Fixture",
      link: "/watch/episode-1",
      cover: "/covers/video.jpg",
      intro: "Synthetic video search result",
      category: "Video"
    }
  ]);
}
