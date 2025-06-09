module.exports = {
  title: "CabinJ", // your framework’s name
  tagline: "Lightweight Java Web Framework",
  url: "https://cabinj.com", // change to your production URL
  baseUrl: "/", // so pages live under /docs/*
  favicon: "img/logo.png",
  organizationName: "CabinJV", // for deploymentj
  projectName: "cabinJ", // repo name

  themeConfig: {
    metadata: [
      { name: "keywords", content: "CabinJ, Java, Web Framework, Lightweight, Fast" },
      { name: "description", content: "CabinJ is a lightweight Java web framework designed for simplicity and performance." },
      { name: "og:title", content: "CabinJ - Lightweight Java Web Framework" },
      { name: "og:description", content: "CabinJ is a lightweight Java web framework designed for simplicity and performance." },
      { name: "og:image", content: "https://cabinj.com/img/logo-m.png" },
      { name: "og:url", content: "https://cabinj.com" },
      { name: "twitter:card", content: "summary_large_image" },
      { name: "twitter:site", content: "@CabinJV" },
      { name: "twitter:title", content: "CabinJ - Lightweight Java Web Framework" },
      { name: "twitter:description", content: "CabinJ is a lightweight Java web framework designed for simplicity and performance." },
      { name: "twitter:image", content: "https://cabinj.com/img/logo-m.png" },
    ],
    navbar: {
      title: "CabinJ",
      logo: { alt: "CabinJ Logo", src: "img/logo.png" },
      items: [
        { to: "getting-started/installation", label: "Docs", position: "left" },
        {
          href: "https://github.com/CabinJV/CabinJv",
          label: "GitHub",
          position: "right",
        },
        {
          type: "docsVersionDropdown", // versioning control
          position: "right",
        },
      ],
    },
    footer: {
      links: [
      ],
      copyright: `© ${new Date().getFullYear()} CabinJV`,
    },
    prism: {
      additionalLanguages: ["java"], // enable Java syntax highlighting
    },
  },

  presets: [
    [
      "classic",
      {
        docs: {
          sidebarPath: require.resolve("./sidebars.js"),
          editUrl: "https://github.com/CabinJV/CabinJv/edit/main/docs/",
          routeBasePath: "/", // serve docs at site root under /docs
          includeCurrentVersion: true,
        },
        blog: false,
        theme: { customCss: require.resolve("./src/css/custom.css") },
      },
    ],
  ],
};
